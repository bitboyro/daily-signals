#!/usr/bin/env python3
"""List changed JUnit classes for a PR; Maven's full unit gate still always runs."""
import pathlib
import subprocess
import sys

base=sys.argv[1] if len(sys.argv)>1 else 'HEAD^'
changed=subprocess.check_output(['git','diff','--name-only',base,'HEAD'],text=True).splitlines()
for name in changed:
    path=pathlib.PurePosixPath(name)
    if '/src/test/java/' in name and path.name.endswith('Test.java'):
        print(path.stem)
