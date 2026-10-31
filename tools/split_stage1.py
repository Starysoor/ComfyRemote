import io

SRC = 'app/src/main/java/com/comfyremote/app/MainActivity.kt'
lines = io.open(SRC, encoding='utf-8').read().split('\n')

def seg(a, b):
    """1-based inclusive slice."""
    return '\n'.join(lines[a - 1:b])

IMPORTS = seg(2, 117)  # original import block
PKG_MAIN = 'package com.comfyremote.app'

HEADER = '''{pkg}

{imports}

import com.comfyremote.app.*
'''

files = {
    'app/src/main/java/com/comfyremote/app/feature/connect/ConnectScreen.kt':
        ('com.comfyremote.app.feature.connect', seg(169, 360)),
    'app/src/main/java/com/comfyremote/app/feature/ai/AiSettingsScreen.kt':
        ('com.comfyremote.app.feature.ai', seg(361, 560)),
    'app/src/main/java/com/comfyremote/app/feature/update/UpdateScreen.kt':
        ('com.comfyremote.app.feature.update', seg(561, 651)),
    'app/src/main/java/com/comfyremote/app/feature/workflows/WorkflowsScreen.kt':
        ('com.comfyremote.app.feature.workflows', seg(652, 719)),
    'app/src/main/java/com/comfyremote/app/feature/history/HistoryScreen.kt':
        ('com.comfyremote.app.feature.history', seg(720, 879)),
    'app/src/main/java/com/comfyremote/app/feature/generate/GenerateScreen.kt':
        ('com.comfyremote.app.feature.generate', seg(880, 1213)),
    'app/src/main/java/com/comfyremote/app/feature/generate/GenerateDialogs.kt':
        ('com.comfyremote.app.feature.generate', seg(1214, 1387)),
    'app/src/main/java/com/comfyremote/app/feature/generate/ImageViewer.kt':
        ('com.comfyremote.app.feature.generate', seg(1388, 1456)),
}

import os
for path, (pkg, body) in files.items():
    os.makedirs(os.path.dirname(path), exist_ok=True)
    content = HEADER.format(pkg=pkg, imports=IMPORTS) + '\n' + body + '\n'
    io.open(path, 'w', encoding='utf-8', newline='\n').write(content)
    print('wrote', path, len(content.split('\n')), 'lines')

# New MainActivity: package + imports + MainActivity + App (119-168)
new_main = PKG_MAIN + '\n\n' + IMPORTS + '\nimport com.comfyremote.app.ui.ComfyTheme\nimport com.comfyremote.app.ui.L10n\nimport com.comfyremote.app.ui.Mono\n\n' + seg(119, 168) + '\n'
io.open(SRC, 'w', encoding='utf-8', newline='\n').write(new_main)
print('MainActivity trimmed to', len(new_main.split('\n')), 'lines')
