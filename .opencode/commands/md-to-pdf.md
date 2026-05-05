---
description: Converte un file Markdown in PDF usando md-to-pdf
---
Converte il file Markdown specificato in un PDF con formattazione professionale usando md-to-pdf.

File da convertire: $ARGUMENTS

Se non viene specificato alcun file, converti il file DOCUMENTAZIONE_TECNICA.md nella directory docs/guidelines/.

Istruzioni:
1. Verifica che md-to-pdf sia installato globalmente (npm list -g md-to-pdf)
2. Esegui la conversione del file Markdown specificato in PDF nella stessa directory del file sorgente
3. Il file PDF deve avere lo stesso nome del file Markdown ma con estensione .pdf
4. Usa opzioni di formattazione professionali per il PDF (CSS inline se necessario)

Comando da eseguire:
```bash
md-to-pdf "path/to/file.md" --pdf-options '{"format": "A4", "margin": {"top": "20mm", "bottom": "20mm", "left": "15mm", "right": "15mm"}}'
```

Dopo la conversione, verifica che il file PDF sia stato creato correttamente e comunica il percorso del file generato.
