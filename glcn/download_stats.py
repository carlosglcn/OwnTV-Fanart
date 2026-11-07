#!/usr/bin/env python3
"""glcn: a download total that never goes down.

GitHub counts downloads per release asset, and the count goes when the release goes: publishing keeps only the
three newest versions and makes the fixed Downloader link (glcn-latest) anew each time, so the plain total badge
fell back at every release. This keeps the highest count ever seen for each release — the fixed link by its
release id, as its tag is reused — in downloads.json on the `stats` branch, and the README badge reads its total.

Run before a release is deleted (the publish step) and every few hours (glcn-downloads.yml). Needs `gh` signed in
and a checkout that may push. Usage: download_stats.py [owner/repo]
"""
import json
import os
import subprocess
import sys
import tempfile

BRANCH = "stats"
FILE = "downloads.json"


def run(*cmd, cwd=None, check=True):
    return subprocess.run(cmd, cwd=cwd, check=check, capture_output=True, text=True).stdout


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else os.environ["GITHUB_REPOSITORY"]
    lines = run("gh", "api", "--paginate", f"repos/{repo}/releases",
                "-q", ".[] | {id, tag: .tag_name, n: ([.assets[] | select(.name | endswith(\".apk\")) | .download_count] | add // 0)}")
    now = [json.loads(l) for l in lines.splitlines() if l.strip()]

    with tempfile.TemporaryDirectory() as tmp:
        url = f"https://x-access-token:{os.environ['GH_TOKEN']}@github.com/{repo}.git" if os.environ.get("GH_TOKEN") else f"https://github.com/{repo}.git"
        have = subprocess.run(["git", "clone", "-q", "--depth", "1", "-b", BRANCH, url, tmp], capture_output=True).returncode == 0
        if not have:
            run("git", "init", "-q", "-b", BRANCH, tmp)
            run("git", "remote", "add", "origin", url, cwd=tmp)
        path = os.path.join(tmp, FILE)
        stats = json.load(open(path)) if os.path.exists(path) else {"releases": {}}
        rel = stats.setdefault("releases", {})
        for r in now:
            key = r["tag"] if r["tag"].startswith("v") else f"{r['tag']}#{r['id']}"
            rel[key] = max(rel.get(key, 0), r["n"])
        stats["total"] = sum(rel.values()) + stats.get("before", 0)
        with open(path, "w") as f:
            json.dump(stats, f, indent=1, sort_keys=True)
            f.write("\n")
        run("git", "add", FILE, cwd=tmp)
        if not run("git", "status", "--porcelain", cwd=tmp).strip():
            print(f"downloads: {stats['total']} (unchanged)")
            return
        run("git", "-c", "user.name=github-actions", "-c", "user.email=github-actions@users.noreply.github.com",
            "commit", "-q", "-m", f"Downloads: {stats['total']}", cwd=tmp)
        run("git", "push", "-q", "origin", f"HEAD:{BRANCH}", cwd=tmp)
        print(f"downloads: {stats['total']}")


if __name__ == "__main__":
    main()
