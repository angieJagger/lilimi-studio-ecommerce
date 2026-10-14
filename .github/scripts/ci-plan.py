import json
import os
import subprocess


def classify(paths):
    frontend = backend = images = False
    for path in paths:
        if path.endswith('.md') or path.startswith('docs/'):
            continue
        if path.startswith('backend/'):
            backend = images = True
        elif path.startswith(('src/', 'public/')):
            frontend = True
            visual = (
                path.endswith(('.scss', '.css', '.html'))
                or path.startswith('public/')
                or (path.startswith('src/app/core/i18n/') and path.endswith('.json'))
            )
            images = images or not visual
        else:
            # Unknown files, dependencies, Docker and workflow configuration:
            # run all checks rather than accidentally excluding them.
            frontend = backend = images = True
    return {'frontend': frontend, 'backend': backend, 'images': images}


def main():
    if os.environ['GITHUB_EVENT_NAME'] == 'pull_request':
        with open(os.environ['GITHUB_EVENT_PATH'], encoding='utf-8') as source:
            pr = json.load(source)['pull_request']
        revision = f"{pr['base']['sha']}...{pr['head']['sha']}"
        changed = subprocess.check_output(
            ['git', 'diff', '--name-only', '--no-renames', '-z', revision],
        ).decode('utf-8').split('\0')
        plan = classify([path for path in changed if path])
        # A diff failure aborts the planning job and blocks CI result.
        subprocess.run(['git', 'diff', '--check', revision], check=True)
    else:
        # Every main push and manual run verifies both deployable images.
        plan = {
          "frontend": True,
          "backend": True,
          "images": True,
        }

    print('Required checks:', json.dumps(plan))
    with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
        for name, required in plan.items():
            output.write(f'{name}={str(required).lower()}\n')


if __name__ == '__main__':
    main()
