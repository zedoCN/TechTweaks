#!/usr/bin/env python3
"""Prove the optional AE2 reader does not break a server with no AE2 installed."""
import importlib.util,json,os,pathlib,secrets,signal,subprocess,time
ROOT=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('performance',ROOT/'scripts/verify-performance.py');v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
def main():
 run=v.RUN;run.mkdir(parents=True,exist_ok=True);marker=run/'.tech-performance-verification'
 if (run/'world').exists() and not marker.exists():raise RuntimeError('unowned test world')
 marker.touch();port,rp=v.free_port(),v.free_port();password=secrets.token_hex(24)
 while rp==port:rp=v.free_port()
 (run/'eula.txt').write_text('eula=true\n')
 (run/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nenable-rcon=true\nrcon.port={rp}\nrcon.password={password}\nonline-mode=false\nlevel-type=minecraft:flat\nview-distance=3\nsimulation-distance=3\ngenerate-structures=false\n')
 client=None
 with (run/'query-no-ae2.log').open('w') as log:
  process=subprocess.Popen([str(ROOT/'gradlew'),'runVerificationServer','--console=plain'],cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
  try:
   deadline=time.monotonic()+180
   while time.monotonic()<deadline:
    if process.poll() is not None:raise RuntimeError('isolated server exited')
    if 'RCON running on ' in (run/'query-no-ae2.log').read_text():client=v.Rcon(rp,password);break
    time.sleep(1)
   if client is None:raise TimeoutError('isolated server startup')
   caps=json.loads(client.command('techquery'));assert caps['ok'] and caps['ae2_available'] is False
   client.command('forceload add 0 0');client.command('setblock 0 80 0 minecraft:barrel')
   block=json.loads(client.command('techquery {"query":"block","x":0,"y":80,"z":0}'));assert block['id']=='minecraft:barrel' and block['inventory_supported'] and block['matched']==0
   nbt=json.loads(client.command('techquery {"query":"nbt.block","x":0,"y":80,"z":0}'));assert nbt['ok'] and 'minecraft:barrel' in nbt['snbt']
   absent=json.loads(client.command('techquery {"query":"ae2.network","x":0,"y":80,"z":0}'));assert not absent['ok'] and 'not installed' in absent['error']
   (ROOT/'runs/evidence/query-no-ae2.json').write_text(json.dumps({'checks':['capability reports AE2 absent','native container query works','live NBT works','AE2 query explicitly unsupported'],'passed':True},indent=2)+'\n')
   print('NO_AE2_QUERY_PASS 4',flush=True)
  finally:
   if client:
    try:client.command('stop')
    except (OSError,ConnectionError):pass
    client.client.close()
   try:process.wait(timeout=60)
   except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGTERM);process.wait(timeout=15)
   (run/'server.properties').unlink(missing_ok=True)
if __name__=='__main__':main()
