#!/usr/bin/env python3
"""Exercise the production Python snapshot/extraction functions against an isolated NeoForge world."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import secrets
import shutil
import signal
import subprocess
import time
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / 'runs/backup-verification'


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--infra-ops', required=True, type=Path)
    args = parser.parse_args()
    helpers = module('performance_verification', ROOT / 'scripts/verify-performance.py')
    snapshot = module('world_snapshot', args.infra_ops / 'scripts/deploy/minecraft-hbm/world-snapshot.py')
    admin = module('admin_control', args.infra_ops / 'scripts/deploy/minecraft-hbm/admin-control.py')
    RUN.mkdir(parents=True, exist_ok=True)
    marker = RUN / '.tech-backup-verification'
    if (RUN / 'world').exists() and not marker.is_file():
        raise RuntimeError('Refusing to replace an unmarked test world')
    marker.touch()
    for name in ['world', 'snapshot-repo', 'materialized']:
        p = RUN / name
        if p.exists():
            if p.is_symlink() or p.resolve().parent != RUN.resolve():
                raise RuntimeError('Unexpected verification directory')
            shutil.rmtree(p)
    port, rcon_port = helpers.free_port(), helpers.free_port()
    while port == rcon_port:
        rcon_port = helpers.free_port()
    password = secrets.token_hex(24)
    (RUN / 'eula.txt').write_text('eula=true\n')
    (RUN / 'server.properties').write_text(
        f'server-ip=127.0.0.1\nserver-port={port}\nenable-rcon=true\nrcon.port={rcon_port}\n'
        f'rcon.password={password}\nonline-mode=false\nlevel-type=minecraft:flat\n'
        'generate-structures=false\nview-distance=3\nsimulation-distance=3\nmax-players=4\n'
        'spawn-protection=0\nlevel-seed=0\npause-when-empty-seconds=0\n')
    (RUN / 'world').mkdir()
    (RUN / 'world/rolling_gate.json').write_text(json.dumps({
        'fake_player_resident': True, 'fake_player_reload_action': True,
        'allow_spawning_offline_players': True, 'command_player': 'true'}))
    report = []
    process = connection = log = None

    def check(name, predicate, details=None):
        if not predicate:
            raise AssertionError(name + ': ' + str(details))
        report.append({'name': name, 'details': details})
        print('PASS ' + name, flush=True)

    def start(phase):
        nonlocal process, connection, log
        log_path = RUN / (phase + '.log')
        log = log_path.open('w')
        process = subprocess.Popen([str(ROOT / 'gradlew'), 'runBackupVerificationServer', '--console=plain'],
                                   cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        deadline = time.monotonic() + 300
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError('Isolated server exited; see ' + str(log_path))
            if 'RCON running on ' in log_path.read_text(errors='replace'):
                connection = helpers.Rcon(rcon_port, password)
                connection.client.settimeout(180)
                return
            time.sleep(1)
        raise TimeoutError('Isolated server not ready; see ' + str(log_path))

    def stop():
        nonlocal process, connection, log
        if connection:
            try:
                connection.command('stop')
            except (OSError, ConnectionError):
                pass
            connection.client.close()
            connection = None
        if process:
            try:
                process.wait(timeout=60)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGTERM)
                process.wait(timeout=20)
            process = None
        if log:
            log.close()
            log = None

    def capture(target):
        connection.command('save-off')
        try:
            with patch.object(snapshot, 'rcon', side_effect=connection.command):
                count, digest = snapshot.capture_residents(RUN / 'world')
            snapshot.copy_world(RUN / 'world', target)
            snapshot.validate_residents(target, digest, count)
            return count, json.loads((target / 'fake_player.rg.json').read_text())
        finally:
            connection.command('save-on')

    def git(*args):
        return subprocess.check_output(['git', '-C', str(repo), *args], text=True).strip()

    try:
        start('initial')
        connection.command('forceload add -16 -16 16 16')
        time.sleep(1)
        connection.command('fill -8 60 -8 8 60 8 minecraft:bedrock')
        connection.command('setworldspawn 0 61 0')
        for name, x in [('BackupOne', 1), ('BackupTwo', 3)]:
            connection.command(f'player {name} spawn')
            connection.command(f'gamemode creative {name}')
            connection.command(f'tp {name} {x} 61 1')
        connection.command('give BackupOne minecraft:diamond 3')
        connection.command('player BackupOne hotbar 5')
        connection.command('player BackupOne sneak')
        connection.command('player BackupOne attack interval 37')
        connection.command('player BackupTwo use interval 23')
        repo = RUN / 'snapshot-repo'
        repo.mkdir()
        git('init', '-q'); git('config', 'user.name', 'Backup verification')
        git('config', 'user.email', 'backup@example.invalid'); git('lfs', 'install', '--local')
        (repo / '.gitattributes').write_text('world/** filter=lfs diff=lfs merge=lfs -text\n')
        count, saved = capture(repo / 'world')
        check('captures_two_current_residents', count == 2 and set(saved) == {'BackupOne', 'BackupTwo'})
        check('captures_nonempty_native_action_pack', all(v.get('actions', {}).get('actions') for v in saved.values()), saved)
        git('add', '.gitattributes', 'world'); git('commit', '-qm', 'online snapshot fixture')
        commit = git('rev-parse', 'HEAD')
        check('manifest_stored_as_lfs_object', git('cat-file', '-p', commit + ':world/fake_player.rg.json').startswith('version https://git-lfs.github.com/spec/v1'))
        connection.command('rg fakePlayerReloadAction false')
        with patch.object(snapshot, 'rcon', side_effect=connection.command):
            try:
                snapshot.capture_residents(RUN / 'world')
            except RuntimeError:
                check('disabled_action_restore_refuses_snapshot', True)
            else:
                raise AssertionError('Missing restoration rule was accepted')
        connection.command('rg fakePlayerReloadAction true')
        for name in saved:
            connection.command('player ' + name + ' kill')
        empty = RUN / 'empty-world-copy'
        if empty.exists():
            shutil.rmtree(empty)
        count, states = capture(empty)
        check('empty_roster_does_not_resurrect_departed_players', count == 0 and states == {})
        shutil.rmtree(empty)
        stop()
        extracted = RUN / 'materialized'
        admin.materialize(repo, commit, extracted)
        check('restore_extraction_preserves_roster_and_actions', json.loads((extracted / 'fake_player.rg.json').read_text()) == saved)
        shutil.rmtree(RUN / 'world')
        extracted.rename(RUN / 'world')
        start('restored')
        proxy = type('Helper', (), {'rcon': staticmethod(connection.command)})()
        admin.wait_residents(proxy, set(saved))
        check('native_startup_restores_both_fake_players', True)
        check('native_loader_consumes_manifest', not (RUN / 'world/fake_player.rg.json').exists())
        inventory = connection.command('data get entity BackupOne Inventory')
        check('restores_inventory', 'minecraft:diamond' in inventory and 'count: 3' in inventory, inventory)
        slot = connection.command('data get entity BackupOne SelectedItemSlot')
        check('restores_selected_hotbar_slot', slot.rstrip().endswith('4'), slot)
        pos = connection.command('data get entity BackupOne Pos')
        check('restores_position', '1.0d' in pos and '61.0d' in pos, pos)
        count, resumed = capture(repo / 'world')
        for name in saved:
            original = saved[name]['actions']; current = resumed[name]['actions']
            check('restores_action_intervals_' + name,
                  {k: v.get('interval') for k, v in original['actions'].items()} ==
                  {k: v.get('interval') for k, v in current['actions'].items()}, current)
        check('restores_sneaking', resumed['BackupOne']['actions'].get('sneaking') is True)
        (RUN / 'verification.json').write_text(json.dumps({'commit': commit, 'checks': report}, ensure_ascii=False, indent=2))
        print('BACKUP_RESTORE_VERIFICATION_PASS', flush=True)
    finally:
        stop()
        (RUN / 'server.properties').unlink(missing_ok=True)


if __name__ == '__main__':
    main()
