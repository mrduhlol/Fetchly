from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
P=Path(__file__).parent; repo=P.parents[1]; out=P/'exports'
with ZipFile(out/'fetchly-visual-assets.zip','w',ZIP_DEFLATED) as z:
 for p in (P/'assets').rglob('*'):
  if p.is_file() and p.suffix in {'.png','.svg','.ttf','.txt'}: z.write(p,p.relative_to(P))
with ZipFile(out/'fetchly-editable-source.zip','w',ZIP_DEFLATED) as z:
 for p in P.rglob('*'):
  r=p.relative_to(P)
  if not p.is_file() or any(x in {'.venv','.hyperframes','exports','validation','node_modules','__pycache__'} for x in r.parts): continue
  z.write(p,Path('motion/fetchly-splash')/r)
 for name in ['app/src/main/java/com/fetchly/app/ui/splash/FetchlySplash.kt','app/src/main/java/com/fetchly/app/MainActivity.kt','app/src/main/res/drawable-nodpi/splash_apollo.webp','app/src/main/res/drawable-nodpi/splash_wordmark.webp','app/src/main/res/drawable/fetchly_splash.xml','app/src/main/res/drawable/splash_empty.xml','app/src/main/res/values-v31/themes.xml']:
  z.write(repo/name,Path('android-integration')/name)
print('Packaged independent assets and editable source + native integration')
