#!/usr/bin/env python3
"""Pointer probe runner test double. Implements only permitted ADB calls."""
import json
import os
from pathlib import Path
import sys

args = sys.argv[1:]
root = Path(os.environ["PROBE_RUNNER_FIXTURE"])
commit = "0123456789abcdef" * 2 + "01234567"
scenario = os.environ["PROBE_RUNNER_SCENARIO"]
with (root / "adb.jsonl").open("a") as log:
    print(json.dumps({"args": args}), file=log)

serial = "127.0.0.1:55555"
package = "com.pokedaisy.mousesupremacy.probe"
guest = "unauthorized" if scenario == "unauthorized" else "device"
if args == ["devices", "-l"]:
    print(f"List of devices attached\nother-target\tdevice\n{serial}\t{guest}")
    sys.exit()
assert args[:2] == ["-s", serial], args
args = args[2:]

if args == ["get-state"]:
    print(guest)
elif args[:2] == ["shell", "getprop"]:
    print({
        "ro.build.version.sdk": "33",
        "ro.product.cpu.abilist": "x86_64",
        "ro.product.manufacturer": "other" if scenario == "wrong-guest" else "Microsoft Corporation",
        "ro.hardware": "ranchu" if scenario == "wrong-guest" else "windows_fallback",
    }[args[2]])
elif args[:2] == ["install", "-r"]:
    assert Path(args[2]).name == "mousesupremacy-pointer-probe.apk"
    if scenario == "signer":
        print("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]")
        sys.exit(1)
    print("Success")
elif args == ["shell", "pm", "path", package]:
    print("package:/data/app/probe/base.apk")
elif args[:6] == ["shell", "am", "start", "-W", "-S", "-n"]:
    assert args[6] == f"{package}/{package}.ProbeActivity", args
    extras = dict((args[index + 1], args[index + 2]) for index in range(7, len(args), 3))
    (root / "session.json").write_text(json.dumps(extras))
    print("Status: ok")
elif args == ["exec-out", "run-as", package, "cat", "files/pointer-trace.json"]:
    trace = json.loads((root / "session.json").read_text())
    trace.update(schemaVersion=1, capacity=4096, sourceCommit=commit, observedCount=3,
                 coalescedMoves=0, discardedMoves=0, discardedBoundaries=0, events=[
                     {"kind": "start", "seq": 1},
                     {"kind": "layout", "seq": 2, "reason": "capture-start"},
                     {"kind": "end", "seq": 3},
                 ])
    if scenario == "stale":
        trace["session"] = "0" * 32
    if scenario == "truncated":
        trace["events"].pop(0)
        trace["discardedBoundaries"] = 1
    if scenario == "missing-layout":
        trace["events"][1]["reason"] = "other"
    print(json.dumps(trace))
else:
    raise AssertionError(f"Out-of-scope ADB call: {args!r}")
