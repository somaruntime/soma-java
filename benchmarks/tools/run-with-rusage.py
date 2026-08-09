#!/usr/bin/env python3
"""Run one command and emit portable wall/CPU/max-RSS evidence.

macOS `/usr/bin/time -l` consults restricted sysctl state before printing
resource usage.  That makes a successful benchmark look failed inside a
sandbox.  `wait4(2)` returns the same child rusage without that host query.
"""

from __future__ import annotations

import argparse
import os
import sys
import time


def parse_args() -> list[str]:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command
    if command and command[0] == "--":
        command = command[1:]
    if not command:
        parser.error("a command is required after --")
    return command


def main() -> int:
    command = parse_args()
    started = time.monotonic()
    pid = os.fork()
    if pid == 0:
        try:
            os.execv(command[0], command)
        except BaseException as error:
            print("rusage: exec failed: {}".format(error), file=sys.stderr)
            os._exit(127)

    _, status, usage = os.wait4(pid, 0)
    elapsed = time.monotonic() - started
    print(
        "{:.2f} real {:.2f} user {:.2f} sys".format(
            elapsed, usage.ru_utime, usage.ru_stime
        ),
        file=sys.stderr,
    )
    if sys.platform == "darwin":
        print(
            "{} maximum resident set size".format(usage.ru_maxrss),
            file=sys.stderr,
        )
    else:
        print(
            "Maximum resident set size (kbytes): {}".format(usage.ru_maxrss),
            file=sys.stderr,
        )

    if os.WIFEXITED(status):
        return os.WEXITSTATUS(status)
    if os.WIFSIGNALED(status):
        return 128 + os.WTERMSIG(status)
    return 1


if __name__ == "__main__":
    sys.exit(main())
