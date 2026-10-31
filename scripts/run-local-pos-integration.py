"""Run POS integration tests in a disposable local PostgreSQL cluster.

Uses installed PostgreSQL tools and Maven; no application credentials are needed.
Test files stay under target/.
"""
import os
import pathlib
import subprocess
import uuid
import shutil
import socket
import argparse
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

parser = argparse.ArgumentParser()
parser.add_argument('--tests', default='PosEnhancementsIT')
args = parser.parse_args()

root = pathlib.Path(__file__).resolve().parents[1]
psql = shutil.which('psql')
if not psql:
    raise SystemExit('Install PostgreSQL and put its bin directory on PATH.')
bin_dir = pathlib.Path(psql).parent
suffix = '.exe' if os.name == 'nt' else ''
cluster = root / 'target' / ('pos-test-' + uuid.uuid4().hex)
cluster.parent.mkdir(exist_ok=True)
with socket.socket() as candidate:
    candidate.bind(('127.0.0.1', 0))
    port = candidate.getsockname()[1]

def run(command, **options):
    return subprocess.run([str(item) for item in command], cwd=root,
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0, **options)

run([bin_dir / ('initdb' + suffix), '-D', cluster, '-U', 'pos_test',
    '-A', 'trust', '--no-locale', '-E', 'UTF8'], check=True, stdout=subprocess.DEVNULL)
started = False
try:
    run([bin_dir / ('pg_ctl' + suffix), '-D', cluster, '-l', cluster / 'server.log',
        '-o', f'-h 127.0.0.1 -p {port}', '-w', 'start'], check=True, stdout=subprocess.DEVNULL)
    started = True
    print('Started isolated PostgreSQL test cluster on port ' + str(port), flush=True)
    test_env = dict(os.environ)
    test_env.update(FASHIONPOS_TEST_DB_URL=f'jdbc:postgresql://127.0.0.1:{port}/postgres',
        FASHIONPOS_TEST_DB_USERNAME='pos_test', FASHIONPOS_TEST_DB_PASSWORD='')
    result = run(['mvn.cmd' if os.name == 'nt' else 'mvn', '-q',
        '-Dtest=' + args.tests, 'test'], env=test_env, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
    print(result.stdout, flush=True)
    exit_code = result.returncode
finally:
    if started:
        run([bin_dir / ('pg_ctl' + suffix), '-D', cluster, '-m', 'fast', '-w', 'stop'],
            check=True, stdout=subprocess.DEVNULL)
        print('Stopped isolated test cluster. Files remain under target/.', flush=True)
raise SystemExit(exit_code)
