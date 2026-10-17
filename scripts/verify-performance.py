#!/usr/bin/env python3
"""Run an isolated loopback-only NeoForge server and check the production mixins."""
import json
import os
from pathlib import Path
import re
import secrets
import signal
import shutil
import socket
import struct
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / 'runs' / 'performance-verification'


class Rcon:
    def __init__(self, port, password):
        self.client = socket.create_connection(('127.0.0.1', port), timeout=5)
        self.client.settimeout(10)
        self.send(1, 3, password)
        for _ in range(2):
            request, kind, _ = self.receive()
            if kind == 2:
                if request != 1:
                    raise RuntimeError('isolated RCON authentication failed')
                return
        raise RuntimeError('missing isolated RCON response')

    def send(self, request, kind, text):
        data = struct.pack('<ii', request, kind) + text.encode() + b'\0\0'
        self.client.sendall(struct.pack('<i', len(data)) + data)

    def receive(self):
        def take(size):
            result = b''
            while len(result) < size:
                part = self.client.recv(size - len(result))
                if not part:
                    raise ConnectionError('isolated RCON disconnected')
                result += part
            return result
        length = struct.unpack('<i', take(4))[0]
        if not 10 <= length <= 1024 * 1024:
            raise ValueError('invalid isolated RCON packet')
        body = take(length)
        request, kind = struct.unpack('<ii', body[:8])
        return request, kind, body[8:-2].decode()

    def command(self, command):
        self.send(2, 2, command)
        request, _, output = self.receive()
        if request != 2:
            raise RuntimeError('isolated RCON response mismatch')
        if any(message in output for message in ['Unknown', 'Incorrect', 'Expected', 'not a valid', 'not loaded']):
            raise RuntimeError(command + ': ' + output)
        return output


def free_port():
    with socket.socket() as client:
        client.bind(('127.0.0.1', 0))
        return client.getsockname()[1]


def verify(rcon):
    checks = []
    def check(name, predicate, details):
        if not predicate:
            raise AssertionError(name + ': ' + str(details))
        checks.append({'name': name, 'details': details})
        print('PASS ' + name, flush=True)

    rcon.command('forceload add -16 -16 64 32')
    time.sleep(1)
    rcon.command('gamerule minecraft:spawn_mobs false')
    # A real Pipez extractor feeding two branches. Only the extractor has HAS_DATA.
    for x, z in [(0, 0), (3, -2), (3, 2)]:
        rcon.command(f'setblock {x} 80 {z} minecraft:barrel')
    for x, z in [(2, 0), (3, 0), (3, -1), (3, 1)]:
        rcon.command(f'setblock {x} 80 {z} pipez:item_pipe')
    rcon.command('setblock 1 80 0 pipez:item_pipe[has_data=true]'
                 '{ExtractingSides:[0b,0b,0b,0b,1b,0b],Upgrades:[{Slot:4b,'
                 'id:"pipez:ultimate_upgrade",count:1,components:{"pipez:item":'
                 '{distribution:2,redstone_mode:0,filter_mode:0,Filters:[]}}}]}')
    rcon.command('data merge block 0 80 0 {Items:[{Slot:0b,id:"minecraft:diamond",count:63},'
                 '{Slot:1b,id:"minecraft:emerald",count:64}]}')
    time.sleep(0.6)
    a = rcon.command('data get block 3 80 -2 Items')
    b = rcon.command('data get block 3 80 2 Items')
    def items(output):
        result = {}
        for entry in re.findall(r'\{([^{}]+)\}', output):
            item = re.search(r'id: "([^"]+)"', entry)
            amount = re.search(r'count: (\d+)', entry)
            if item and amount:
                result[item[1]] = result.get(item[1], 0) + int(amount[1])
        return result
    aa, bb = items(a), items(b)
    check('real_pipe_round_robin_balances_mixed_items_and_remainder',
          sorted([aa.get('minecraft:diamond', 0), bb.get('minecraft:diamond', 0)]) == [31, 32]
          and aa.get('minecraft:emerald') == bb.get('minecraft:emerald') == 32, [a, b])
    stats = rcon.command('tech performance')
    match = re.search(r'pipe_items=(\d+)', stats)
    check('real_pipe_executes_batch_mixin', match is not None and int(match[1]) >= 124, stats)

    # The exact production fallback: repeated copies of a resource rejected by a
    # machine input. A side-facing furnace accepts fuel, never diamonds.
    rcon.command('setblock 16 80 16 minecraft:barrel')
    rcon.command('setblock 18 80 16 minecraft:furnace')
    rcon.command('setblock 17 80 16 pipez:item_pipe[has_data=true]'
                 '{ExtractingSides:[0b,0b,0b,0b,1b,0b],Upgrades:[{Slot:4b,'
                 'id:"pipez:ultimate_upgrade",count:1,components:{"pipez:item":'
                 '{distribution:2,redstone_mode:0,filter_mode:0,Filters:[]}}}]}')
    rcon.command('data merge block 16 80 16 {Items:[{Slot:0b,id:"minecraft:diamond",count:64},'
                 '{Slot:1b,id:"minecraft:diamond",count:64},{Slot:2b,id:"minecraft:diamond",count:64}]}')
    time.sleep(0.5)
    stats = rcon.command('tech performance')
    skipped = re.search(r'pipe_rejects_skipped=(\d+)', stats)
    check('real_pipe_skips_duplicate_rejected_probes', skipped is not None and int(skipped[1]) > 0, stats)
    unchanged = items(rcon.command('data get block 16 80 16 Items'))
    check('rejection_optimization_preserves_source_items', unchanged == {'minecraft:diamond': 192}, unchanged)

    # Identical landing state makes vanilla setBlock return false. The aged leaf
    # must disappear while young leaves, sand and NBT-carrying leaves survive.
    for x in [16, 20, 24, 28, 32]:
        rcon.command(f'setblock {x} 79 0 minecraft:stone')
    rcon.command('setblock 16 80 0 hbm:leaves_layer')
    rcon.command('setblock 20 80 0 hbm:leaves_layer')
    rcon.command('setblock 24 80 0 minecraft:moving_piston')
    rcon.command('setblock 28 80 0 hbm:leaves_layer')
    rcon.command('setblock 32 80 0 minecraft:moving_piston')
    for x, state, age, tag, extra in [
        (16, 'hbm:leaves_layer', 601, 'old_leaf', ''),
        (20, 'hbm:leaves_layer', 10, 'young_leaf', ''),
        (24, 'minecraft:sand', 601, 'sand_control', ''),
        (28, 'hbm:leaves_layer', 601, 'payload_control', ',TileEntityData:{fixture:1b}'),
        (32, 'hbm:leaves_layer', 601, 'piston_control', ''),
    ]:
        rcon.command(f'summon minecraft:falling_block {x + .5} 80 0.5 '
                     f'{{BlockState:{{Name:"{state}"}},Time:{age},DropItem:0b,Tags:["{tag}"]{extra}}}')
    time.sleep(0.6)
    check('old_failed_leaf_removed', 'failed' in rcon.command('execute if entity @e[tag=old_leaf]'), 'old_leaf absent')
    for tag in ['young_leaf', 'sand_control', 'payload_control', 'piston_control']:
        output = rcon.command(f'execute if entity @e[tag={tag}]')
        check(tag + '_preserved', 'Count: 1' in output, output)
    stats = rcon.command('tech performance')
    match = re.search(r'stale_leaves=(\d+)', stats)
    check('leaf_mixin_actually_discarded_residue', match is not None and int(match[1]) >= 1, stats)
    return {'checks': checks, 'counters': stats}


def main():
    RUN.mkdir(parents=True, exist_ok=True)
    marker = RUN / '.tech-performance-verification'
    if (RUN / 'world').exists() and not marker.exists():
        raise RuntimeError('refusing to reuse an unowned world')
    marker.touch()
    world = RUN / 'world'
    if world.exists():
        if world.is_symlink() or world.resolve().parent != RUN.resolve():
            raise RuntimeError('unexpected verification world path')
        shutil.rmtree(world)
    port, rcon_port = free_port(), free_port()
    while port == rcon_port:
        rcon_port = free_port()
    password = secrets.token_hex(24)
    (RUN / 'eula.txt').write_text('eula=true\n')
    (RUN / 'server.properties').write_text(
        f'server-ip=127.0.0.1\nserver-port={port}\nenable-rcon=true\nrcon.port={rcon_port}\n'
        f'rcon.password={password}\nonline-mode=false\nlevel-type=minecraft:flat\n'
        'generate-structures=false\nview-distance=3\nsimulation-distance=3\nmax-players=1\n'
        'spawn-protection=0\nlevel-seed=0\n')
    log_path = RUN / 'verification-console.log'
    rcon = None
    command = [str(ROOT / 'gradlew'), 'runVerificationServer', '--console=plain']
    if os.environ.get('TECH_TEST_JAVA_PATHS'):
        command.append('-Dorg.gradle.java.installations.paths=' + os.environ['TECH_TEST_JAVA_PATHS'])
    with log_path.open('w') as log:
        process = subprocess.Popen(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            deadline = time.monotonic() + 240
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError(f'isolated server exited {process.returncode}; see {log_path}')
                text = log_path.read_text(errors='replace')
                if 'RCON running on ' in text:
                    rcon = Rcon(rcon_port, password)
                    break
                time.sleep(1)
            if rcon is None:
                raise TimeoutError(f'isolated server not ready; see {log_path}')
            print('Isolated server ready', flush=True)
            report = verify(rcon)
            (RUN / 'verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
            print('PERFORMANCE_VERIFICATION_PASS', flush=True)
        finally:
            if rcon is not None:
                try:
                    rcon.command('stop')
                except (OSError, ConnectionError):
                    pass
                rcon.client.close()
            try:
                process.wait(timeout=60)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGTERM)
                process.wait(timeout=15)
            # The ephemeral credential never becomes part of saved evidence.
            (RUN / 'server.properties').unlink(missing_ok=True)


if __name__ == '__main__':
    main()
