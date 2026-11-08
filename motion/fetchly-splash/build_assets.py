"""Rebuild vector masters and 4K graphics; never resamples Apollo."""
from pathlib import Path
import math, json
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
import cairosvg
P=Path(__file__).parent; A=P/'assets'
font=TTFont(A/'fonts/Cinzel.ttf'); glyphs=font.getGlyphSet(); cmap=font.getBestCmap()
units=font['head'].unitsPerEm; scale=190/units; advances=font['hmtx'].metrics
paths=[]; x=0
for i,ch in enumerate('FETCHLY'):
    name=cmap[ord(ch)]; pen=SVGPathPen(glyphs); glyphs[name].draw(pen)
    paths.append(f'<g id="letter-{i}" transform="translate({x:.3f},215) scale({scale},-{scale})"><path d="{pen.getCommands()}"/></g>')
    x+=advances[name][0]*scale+32
width=x-32
word=f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width:.3f} 250"><defs><linearGradient id="marble" x2="0.2" y2="1"><stop stop-color="#fff8e9"/><stop offset=".38" stop-color="#e9ddc7"/><stop offset=".49" stop-color="#fff8eb"/><stop offset=".53" stop-color="#bbaa89"/><stop offset=".58" stop-color="#ecdfc5"/><stop offset="1" stop-color="#c8b083"/></linearGradient></defs><g fill="url(#marble)" stroke="#f7e6c4" stroke-width="1">{''.join(paths)}</g></svg>'''
(A/'fetchly-wordmark.svg').write_text(word)
# Two mirrored branches, short centre gap; acute laurel leaves on broad curved stems.
leaves=[]
for i in range(9):
    x=575+i*49; y=186-0.00044*(x-555)**2
    for side in [0,1]:
        angle=(18+i*1.3 if side==0 else 67-i*1.5)
        d='M 0 0 C -23 -18 -22 -60 0 -91 C 26 -66 27 -22 0 0 Z'
        leaves.append(f'<g class="leaf leaf-{i}" transform="translate({x:.2f} {y:.2f}) rotate({angle})"><path d="{d}" fill="url(#gold)" stroke="#b89a56" stroke-width="1"/><path d="M0 -5 Q3 -40 0 -82" fill="none" stroke="#f2d996" stroke-opacity=".65" stroke-width="1.1"/></g>')
branch='<path class="stem" d="M565 190 Q790 180 1030 74" fill="none" stroke="url(#gold)" stroke-width="5" stroke-linecap="round"/>'+''.join(leaves)
laurel=f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1100 260"><defs><linearGradient id="gold" x1="0" y1="0" x2=".7" y2="1"><stop stop-color="#705329"/><stop offset=".28" stop-color="#dfc183"/><stop offset=".49" stop-color="#f5df9d"/><stop offset=".55" stop-color="#9b783b"/><stop offset="1" stop-color="#c3a25b"/></linearGradient></defs><g id="right">{branch}</g><g id="left" transform="translate(1100 0) scale(-1 1)">{branch}</g></svg>'''
(A/'laurel-divider.svg').write_text(laurel)
background='''<svg xmlns="http://www.w3.org/2000/svg" width="2160" height="3840" viewBox="0 0 2160 3840"><defs><radialGradient id="b"><stop stop-color="#231c12"/><stop offset=".5" stop-color="#0d0c0a"/><stop offset="1" stop-color="#030404"/></radialGradient></defs><rect width="2160" height="3840" fill="#030404"/><ellipse cx="1080" cy="1610" rx="1120" ry="1630" fill="url(#b)"/></svg>'''
(A/'background.svg').write_text(background)
light='''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 2160 3840"><defs><radialGradient id="g"><stop stop-color="#e4b45b" stop-opacity=".19"/><stop offset=".35" stop-color="#b38c3e" stop-opacity=".07"/><stop offset="1" stop-color="#a67d35" stop-opacity="0"/></radialGradient><filter id="soft"><feGaussianBlur stdDeviation="48"/></filter><linearGradient id="r" x2="0" y2="1"><stop stop-color="#d9b56f" stop-opacity="0"/><stop offset=".25" stop-color="#d9b56f" stop-opacity=".065"/><stop offset="1" stop-color="#d9b56f" stop-opacity="0"/></linearGradient></defs><ellipse cx="1080" cy="1350" rx="1020" ry="1350" fill="url(#g)"/><path d="M1350 0 L400 2850 L1800 2850 Z" fill="url(#r)" filter="url(#soft)"/><path d="M1580 0 L780 2600 L1330 2600 Z" fill="url(#r)" filter="url(#soft)"/></svg>'''
(A/'golden-light-rays.svg').write_text(light)
particles=''.join(f'<circle cx="{300+(i*347)%1560}" cy="{600+(i*271)%2230}" r="{1.4+i%3*.7}" opacity="{.13+i%4*.08}" fill="#d2ae62"/>' for i in range(24))
(A/'gold-particles.svg').write_text(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 2160 3840">{particles}</svg>')
(A/'loading-bar.svg').write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 760 20"><path d="M0 10H760" stroke="#b19454" stroke-opacity=".22" stroke-width="3"/><path id="fill" d="M0 10H760" stroke="#d6b971" stroke-width="3"/></svg>')
for name in ['fetchly-wordmark','laurel-divider','background','golden-light-rays','gold-particles','loading-bar']:
    sizes={'fetchly-wordmark':(1600,300),'laurel-divider':(1600,378),'background':(2160,3840),'golden-light-rays':(2160,3840),'gold-particles':(2160,3840),'loading-bar':(1520,40)}
    w,h=sizes[name]; cairosvg.svg2png(url=str(A/f'{name}.svg'),write_to=str(A/f'{name}.png'),output_width=w,output_height=h)
(A/'geometry.json').write_text(json.dumps({'wordmarkWidth':width,'wordmarkPaths':paths},indent=2))
print('Built outlined wordmark, open laurel and 4K lighting masters')
