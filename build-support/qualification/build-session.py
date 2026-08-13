#!/usr/bin/env python3
"""Create and verify an ephemeral SOMA build-session manifest."""

from __future__ import print_function

import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile


FORMAT = "SOMA_BUILD_SESSION_V1"
LABEL = re.compile(r"^[a-z0-9][a-z0-9.-]*$")


class SessionError(RuntimeError):
    pass


def command_bytes(arguments, cwd=None, stderr=None):
    try:
        return subprocess.check_output(arguments, cwd=cwd, stderr=stderr)
    except (OSError, subprocess.CalledProcessError) as error:
        raise SessionError("command failed: {}".format(" ".join(arguments))) from error


def sha256_bytes(value):
    return hashlib.sha256(value).hexdigest()


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def git_head(repo):
    try:
        return command_bytes(
            ["git", "-C", str(repo), "rev-parse", "HEAD"],
            stderr=subprocess.DEVNULL,
        ).decode("ascii").strip()
    except SessionError:
        return "UNBORN"


def git_status(repo):
    return command_bytes(
        ["git", "-C", str(repo), "status", "--porcelain=v1", "-z"]
    )


def candidate_fingerprint(repo):
    repo = repo.resolve()
    listed = command_bytes(
        [
            "git",
            "-C",
            str(repo),
            "ls-files",
            "-z",
            "--cached",
            "--others",
            "--exclude-standard",
        ]
    )
    relative_paths = sorted(item for item in listed.split(b"\0") if item)
    digest = hashlib.sha256()
    digest.update(b"SOMA_SOURCE_CANDIDATE_V1\0")
    digest.update(git_head(repo).encode("ascii"))
    digest.update(b"\0")
    digest.update(git_status(repo))
    digest.update(b"\0")
    for raw_relative in relative_paths:
        relative = os.fsdecode(raw_relative)
        path = repo / relative
        digest.update(raw_relative)
        digest.update(b"\0")
        if path.is_symlink():
            digest.update(b"LINK\0")
            digest.update(os.fsencode(os.readlink(str(path))))
        elif path.is_file():
            digest.update(b"FILE\0")
            with path.open("rb") as source:
                for block in iter(lambda: source.read(1024 * 1024), b""):
                    digest.update(block)
        else:
            digest.update(b"MISSING\0")
        digest.update(b"\0")
    return digest.hexdigest()


def toolchain_identity():
    java_home_value = os.environ.get("JAVA_HOME")
    if not java_home_value:
        raise SessionError("JAVA_HOME must select the qualified Java 8 JDK")
    java_home = Path(java_home_value).resolve()
    java = java_home / "bin" / "java"
    if not java.is_file():
        raise SessionError("JAVA_HOME does not contain an executable java tool")
    java_version = command_bytes(
        [str(java), "-version"], stderr=subprocess.STDOUT
    )
    maven_version = command_bytes(["mvn", "-version"], stderr=subprocess.STDOUT)
    return {
        "java.home": str(java_home),
        "java.version.sha256": sha256_bytes(java_version),
        "maven.version.sha256": sha256_bytes(maven_version),
    }


def parse_artifacts(repo, specifications):
    artifacts = {}
    for specification in specifications:
        if "=" not in specification:
            raise SessionError("artifact must use label=path")
        label, raw_path = specification.split("=", 1)
        if not LABEL.match(label) or label in artifacts:
            raise SessionError("invalid or duplicate artifact label: {}".format(label))
        path = Path(raw_path)
        if not path.is_absolute():
            path = repo / path
        path = path.resolve()
        try:
            relative = path.relative_to(repo)
        except ValueError as error:
            raise SessionError("artifact must be inside the repository") from error
        if not path.is_file():
            raise SessionError("artifact is missing: {}".format(relative))
        artifacts[label] = (relative.as_posix(), sha256_file(path))
    return artifacts


def manifest_values(repo, artifacts):
    status = git_status(repo)
    values = {
        "format": FORMAT,
        "git.head": git_head(repo),
        "tree.state": "dirty" if status else "clean",
        "candidate.sha256": candidate_fingerprint(repo),
    }
    values.update(toolchain_identity())
    values["artifact.count"] = str(len(artifacts))
    for label in sorted(artifacts):
        path, digest = artifacts[label]
        values["artifact.{}.path".format(label)] = path
        values["artifact.{}.sha256".format(label)] = digest
    return values


def write_manifest(repo, output, artifacts):
    output = output.resolve()
    try:
        relative = output.relative_to(repo)
    except ValueError as error:
        raise SessionError("manifest must be inside repository target/") from error
    if not relative.parts or relative.parts[0] != "target":
        raise SessionError("manifest must be inside repository target/")
    output.parent.mkdir(parents=True, exist_ok=True)
    values = manifest_values(repo, artifacts)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=output.name + ".", dir=str(output.parent)
    )
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as target:
            for key in sorted(values):
                target.write("{}={}\n".format(key, values[key]))
        os.replace(temporary_name, str(output))
    finally:
        if os.path.exists(temporary_name):
            os.unlink(temporary_name)


def read_manifest(path):
    values = {}
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as error:
        raise SessionError("build-session manifest is missing") from error
    for line in lines:
        if not line or "=" not in line:
            raise SessionError("build-session manifest is malformed")
        key, value = line.split("=", 1)
        if key in values:
            raise SessionError("duplicate build-session property: {}".format(key))
        values[key] = value
    return values


def verify_manifest(repo, path, required):
    values = read_manifest(path)
    if values.get("format") != FORMAT:
        raise SessionError("unsupported build-session format")
    if values.get("candidate.sha256") != candidate_fingerprint(repo):
        raise SessionError("build-session source candidate is stale or foreign")
    identity = toolchain_identity()
    for key, value in identity.items():
        if values.get(key) != value:
            raise SessionError("build-session toolchain does not match: {}".format(key))
    for label in required:
        if not LABEL.match(label):
            raise SessionError("invalid required artifact label: {}".format(label))
        if "artifact.{}.path".format(label) not in values:
            raise SessionError("build-session artifact is missing: {}".format(label))
    labels = set()
    for key in values:
        match = re.match(r"^artifact\.([a-z0-9][a-z0-9.-]*)\.path$", key)
        if match:
            labels.add(match.group(1))
    if values.get("artifact.count") != str(len(labels)):
        raise SessionError("build-session artifact count is inconsistent")
    for label in labels:
        raw_path = values["artifact.{}.path".format(label)]
        path = (repo / raw_path).resolve()
        try:
            path.relative_to(repo)
        except ValueError as error:
            raise SessionError("build-session artifact path escapes repository") from error
        expected = values.get("artifact.{}.sha256".format(label))
        if not path.is_file() or not expected or sha256_file(path) != expected:
            raise SessionError("build-session artifact is stale: {}".format(label))
    return values


def self_test():
    with tempfile.TemporaryDirectory(prefix="soma-build-session-test.") as root:
        repo = Path(root).resolve()
        command_bytes(["git", "init", "-q", str(repo)])
        (repo / ".gitignore").write_text("/target/\n", encoding="utf-8")
        source = repo / "input.txt"
        source.write_text("one\n", encoding="utf-8")
        command_bytes(["git", "-C", str(repo), "add", ".gitignore", "input.txt"])
        artifact = repo / "target" / "artifact.jar"
        artifact.parent.mkdir()
        artifact.write_bytes(b"artifact-one")
        manifest = repo / "target" / "build-session.properties"
        write_manifest(repo, manifest, {"runtime": ("target/artifact.jar", sha256_file(artifact))})
        verify_manifest(repo, manifest, ["runtime"])
        source.write_text("two\n", encoding="utf-8")
        rejected = False
        try:
            verify_manifest(repo, manifest, ["runtime"])
        except SessionError as error:
            if "source candidate" not in str(error):
                raise
            rejected = True
        if not rejected:
            raise SessionError("self-test accepted a mutated source candidate")
        source.write_text("one\n", encoding="utf-8")
        verify_manifest(repo, manifest, ["runtime"])
        artifact.write_bytes(b"artifact-two")
        rejected = False
        try:
            verify_manifest(repo, manifest, ["runtime"])
        except SessionError as error:
            if "artifact is stale" not in str(error):
                raise
            rejected = True
        if not rejected:
            raise SessionError("self-test accepted a mutated artifact")


def parser():
    result = argparse.ArgumentParser()
    result.add_argument("command", choices=("create", "verify", "fingerprint", "self-test"))
    result.add_argument("--repo", default=".")
    result.add_argument("--manifest")
    result.add_argument("--artifact", action="append", default=[])
    result.add_argument("--require", action="append", default=[])
    return result


def main():
    arguments = parser().parse_args()
    repo = Path(arguments.repo).resolve()
    if arguments.command == "self-test":
        self_test()
        print("build-session: self-test PASS")
        return
    if arguments.command == "fingerprint":
        print(candidate_fingerprint(repo))
        return
    if not arguments.manifest:
        raise SessionError("--manifest is required")
    manifest = Path(arguments.manifest)
    if not manifest.is_absolute():
        manifest = repo / manifest
    if arguments.command == "create":
        artifacts = parse_artifacts(repo, arguments.artifact)
        write_manifest(repo, manifest, artifacts)
        print("build-session: created {}".format(manifest.relative_to(repo)))
    else:
        verify_manifest(repo, manifest, arguments.require)
        print("build-session: verified {}".format(manifest.relative_to(repo)))


if __name__ == "__main__":
    try:
        main()
    except SessionError as error:
        print("build-session: {}".format(error), file=sys.stderr)
        sys.exit(1)
