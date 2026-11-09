"""Build the current-UI Fetchly promo as an editable HyperFrames project."""
from pathlib import Path
from html import escape
import json, math
P=Path(__file__).resolve().parent
V=json.loads((P/'latest-narration.json').read_text())
DURATIONS=[5,6.5,6.5,6.5,6,6,6.5,12]
STARTS=[]; t=0
for d in DURATIONS: STARTS.append(t); t+=d
TOTAL=t
SCENES=[
 ('hook','Keep the<br><em>moment.</em>','Your next saved favourite starts here.','latest-home.png','home'),
 ('paste','Paste.<br>Choose.<br><em>Fetch.</em>','A real preview. The formats available.','latest-quality.png','quality'),
 ('audio','Just the<br><em>sound.</em>','MP3 audio · choose your bitrate','latest-audio.png','audio'),
 ('share','Share it.<br><em>Fetch it.</em>','Download options over the source app.','latest-share.png','share'),
 ('batch','One link?<br><em>Or fifty.</em>','Review your links. Choose what to queue.','latest-batch.png','batch'),
 ('library','Saved.<br><em>Sorted.</em>','Your downloads and media, together.','latest-library.png','library'),
 ('appearance','Your app.<br><em>Your look.</em>','Gold or white. Sized for you.','latest-appearance.png','appearance'),
 ('end','Paste. Choose. Fetch.','','','end')]
CSS='''@font-face{font-family:Cinzel;src:url('fonts/Cinzel.ttf')}@font-face{font-family:Manrope;src:url('fonts/Manrope.ttf')}*{box-sizing:border-box}html,body{margin:0;width:1920px;height:1080px;overflow:hidden;background:#172d3a;color:#f5ecda;font-family:Manrope,sans-serif}h1,h2,p{margin:0}.clip{position:absolute;inset:0}.stage{position:absolute;inset:0;overflow:hidden}.ground{position:absolute;inset:0;background:#172d3a}.ground.light{background:#e7dbc2}.ground.bronze{background:#433724}.ground:after{content:'';position:absolute;left:0;bottom:0;width:100%;height:22px;background:#af8f51}.copy{position:absolute;left:192px;top:220px;width:680px;z-index:3}.copy h1{font-family:Cinzel,serif;font-weight:700;font-size:105px;line-height:1.12;letter-spacing:-3px}.copy em{font-style:normal;color:#dfbd78}.copy p{font-size:30px;line-height:1.5;max-width:600px;margin-top:34px;color:#d2c9b8}.light~.copy{color:#30291f}.light~.copy em{color:#885c26}.light~.copy p{color:#5e5140}.screen-frame{position:absolute;left:960px;top:90px;width:768px;height:760px;overflow:hidden;border:2px solid #ad8e55;border-radius:22px;background:#12150f;box-shadow:24px 28px 0 #0b17253b}.screen-frame>img{position:absolute;left:0;width:100%;height:auto;top:-57px}.quality>img{top:-110px}.audio>img{top:-400px}.share>img{top:-325px}.batch>img{top:-78px}.library>img{top:-60px}.appearance>img{top:-90px}.screen-label{position:absolute;left:964px;top:865px;font-size:28px;color:#d2c9b8;letter-spacing:1px}.light~.screen-label{color:#5e5140}.subtitle{position:absolute;left:192px;top:913px;width:1536px;text-align:center;z-index:50;pointer-events:none}.subtitle span{display:inline-block;background:#111611ee;color:#fff6e5;padding:12px 24px;font-family:Manrope,sans-serif;font-size:36px;font-weight:600;line-height:1.35;border-bottom:3px solid #dab778;max-width:1536px}.source-caption{position:absolute;left:192px;top:835px;font-size:25px;color:#c2bba9}.light~.source-caption{color:#625744}.end-logo{position:absolute;left:816px;top:100px;width:288px;height:288px;object-fit:contain}.end-title{position:absolute;top:411px;left:192px;width:1536px;font-family:Cinzel,serif;font-size:82px;text-align:center;color:#e5c68b}.end-repo{position:absolute;top:540px;left:192px;width:1536px;font-size:46px;font-weight:600;text-align:center}.end-button{position:absolute;left:660px;top:645px;width:600px;padding:27px 0;background:#dabb79;color:#282416;text-align:center;font-size:36px;font-weight:800;clip-path:polygon(3% 0,97% 0,100% 18%,100% 82%,97% 100%,3% 100%,0 82%,0 18%)}.end-note{position:absolute;left:192px;top:775px;width:1536px;text-align:center;font-size:29px;color:#d2c9b8}.rule{height:3px;width:120px;background:#b69759;margin-top:30px}.library-secondary{position:absolute;left:950px;top:70px;width:350px;height:778px;overflow:hidden;border:2px solid #ad8e55;border-radius:22px}.library-secondary img{width:100%;position:absolute;top:0;left:0}.library-primary{opacity:0;width:350px;left:1378px;top:70px;height:778px}.library-primary>img{top:0}.end-traits{position:absolute;left:192px;top:300px;width:1536px;display:flex;justify-content:center;gap:100px;font-size:68px}.end-lockup{position:absolute;inset:0}.end-traits strong{color:#e5c68b}.end-traits span{display:block;font-size:30px;margin-top:12px;color:#d2c9b8;text-align:center}
'''
(P/'assets/latest.css').write_text(CSS)
C=P/'compositions'; C.mkdir(exist_ok=True)
hosts=[]; audio=[]; caption_html=[]; srt=[]; cue_no=1

def stamp(v):
 ms=round(v*1000); return f'{ms//3600000:02}:{ms//60000%60:02}:{ms//1000%60:02},{ms%1000:03}'

def aud(id,src,start,duration,volume,track,offset=0,fadein=0,fadeout=0):
 audio.append(f'<audio id="{id}" class="clip" src="{src}" data-start="{start:.4f}" data-duration="{duration:.4f}" data-media-start="{offset}" data-volume="{volume}" data-fade-in="{fadein}" data-fade-out="{fadeout}" data-track-index="{track}"></audio>')

for i,((name,title,body,screen,kind),start,duration,voice) in enumerate(zip(SCENES,STARTS,DURATIONS,V)):
 sid='latest-'+name
 light=name in ('audio','batch','appearance')
 if name!='end':
  extra=''
  if name=='appearance':
   extra=f'<div id="latest-size-panel" class="screen-frame" style="opacity:0"><img src="assets/screens/latest-size.png" alt="Actual app-size controls" data-layout-allow-overflow></div>'
  if name=='library':
   extra='<div id="latest-downloads-panel" class="library-secondary" data-layout-allow-overlap><img src="assets/screens/latest-downloads.png" alt="Current Fetchly downloads screen" data-layout-allow-overflow></div>'
  content=f'''<div class="ground {'light' if light else ''}"></div><div class="copy" id="{sid}-copy"><h1>{title}</h1><div class="rule"></div><p>{body}</p></div><div class="screen-frame {kind} {'library-primary' if name=='library' else ''}" id="{sid}-screen"><img src="assets/screens/{screen}" alt="Actual current Fetchly {kind} screen" data-layout-allow-overflow></div>{extra}<p class="screen-label">Captured from the current app</p>'''
  motion=f'''tl.fromTo('#{sid}-screen',{{x:80,opacity:.5}},{{x:0,opacity:1,duration:.5,ease:'power3.out'}},0);tl.fromTo('#{sid}-copy',{{x:-24,opacity:.5}},{{x:0,opacity:1,duration:.4,ease:'power2.out'}},0);'''
  if name=='hook':
   motion+=f"tl.fromTo('#{sid}-screen img',{{y:0}},{{y:-150,duration:.65,ease:'power3.inOut'}},2.9);"
  if name=='paste':
   motion+=f"tl.fromTo('#{sid}-screen img',{{y:0}},{{y:-640,duration:.6,ease:'power3.inOut'}},2.0);"
  if name=='audio':motion+=f"tl.fromTo('#{sid}-screen img',{{y:0}},{{y:-190,duration:.6,ease:'power3.inOut'}},1.7);"
  if name=='share':motion+=f"tl.fromTo('#{sid}-screen img',{{y:0}},{{y:-330,duration:.6,ease:'power3.inOut'}},2.8);"
  if name=='library':motion="gsap.set('#latest-library-screen',{opacity:0,x:40});tl.fromTo('#latest-downloads-panel',{x:80,opacity:0},{x:0,opacity:1,duration:.45,ease:'power3.out'},0);tl.to('#latest-library-screen',{opacity:1,x:0,duration:.4,ease:'power3.out'},2.1);"
  if name=='batch':motion+=f"tl.fromTo('#{sid}-screen img',{{y:0}},{{y:-600,duration:.6,ease:'power3.inOut'}},2.5);"
  if name=='appearance':motion+=f"tl.fromTo('#latest-size-panel',{{opacity:0}},{{opacity:1,duration:.25}},3.5);"
 else:
  content='''<div class="ground bronze"></div><div id="latest-end-traits" class="end-traits"><div><strong>No account</strong><span>Start with a link</span></div><div><strong>On-device</strong><span>Resolve, download, merge</span></div></div><div id="latest-end-lockup" class="end-lockup" data-hold><img id="latest-end-logo" class="end-logo" src="assets/brand/fetchly-logo.png" alt="Fetchly Apollo icon"><h1 class="end-title">Paste. Choose. Fetch.</h1><p class="end-repo" data-var-text="repo">github.com/mrduhlol/Fetchly</p><div id="latest-end-button" class="end-button">Download on GitHub</div><p class="end-note">Android 10+ · Get the latest APK in Releases</p></div>'''
  motion="""tl.fromTo('#latest-end-lockup',{opacity:0},{opacity:1,duration:.25},3.4);tl.fromTo('#latest-end-traits',{opacity:1},{opacity:0,duration:.2},3.2);tl.fromTo('#latest-end-logo',{y:45,scale:.85},{y:0,scale:1,duration:.6,ease:'power3.out'},3.4);tl.fromTo('#latest-end-button',{scaleX:.1,opacity:0},{scaleX:1,opacity:1,duration:.45,ease:'power3.out'},4.1);"""
 # Scenes remain fully visible through hard cuts; no black seam or unreadable fading titles.
 scene=f'''<template><style>#root{{position:absolute;inset:0;overflow:hidden}}</style><div id="root" data-composition-id="{sid}" data-width="1920" data-height="1080" data-duration="{duration}"><div id="{sid}-clip" class="clip" data-start="0" data-duration="{duration}" data-track-index="1"><div class="stage">{content}</div></div></div><script>const tl=gsap.timeline({{paused:true}});{motion}tl.to({{}},{{duration:.01}},{duration-.02});window.__timelines['{sid}']=tl;</script></template>'''
 (C/f'{sid}.html').write_text(scene)
 hosts.append(f'<div id="{sid}" class="clip" data-composition-id="{sid}" data-composition-src="compositions/{sid}.html" data-start="{start}" data-duration="{duration}" data-width="1920" data-height="1080" data-track-index="1" data-track-kind="graphics"></div>')
 vo_start=start+.45
 assert voice['id']==name
 assert voice['duration']+.6<duration,(name,voice['duration'],duration)
 aud('latest-vo-'+name,voice['path'],vo_start,voice['duration'],.64,3)
 for cue in voice['cues']:
  a=vo_start+cue['start']; b=vo_start+cue['end']
  caption_html.append(f'<div id="caption-{cue_no}" class="clip" data-start="{a:.4f}" data-duration="{b-a:.4f}" data-track-index="2"><div class="subtitle"><span>{escape(cue["text"])}</span></div></div>')
  srt.append(f'{cue_no}\n{stamp(a)} --> {stamp(b)}\n{cue["text"]}\n');cue_no+=1
 if i:
  aud('latest-cut-'+name,'assets/audio/pixabay/whoosh.mp3',start-.22,.64,.045,6,offset=1.65,fadein=.06,fadeout=.12)
 if name in ('paste','audio','batch'):
  aud('latest-click-'+name,'assets/audio/pixabay/click.mp3',start+2.0,.24,.13,7,offset=0,fadeout=.05)
aud('latest-music-lyre','assets/audio/pixabay/lyre.mp3',0,TOTAL,.052,4,offset=2,fadein=.6,fadeout=1.5)
aud('latest-music-rhythm','assets/audio/pixabay/rhythm.mp3',5,TOTAL-5,.028,5,offset=3,fadein=1.2,fadeout=1.5)
aud('latest-logo-impact','assets/audio/pixabay/impact.mp3',STARTS[-1]+3.1,2.5,.09,6,offset=1.4,fadeout=.9)
(C/'latest-captions.html').write_text(f'<template><style>#root{{position:absolute;inset:0;pointer-events:none}}</style><div id="root" data-composition-id="latest-captions" data-width="1920" data-height="1080" data-duration="{TOTAL}">'+''.join(caption_html)+f'</div><script>const tl=gsap.timeline({{paused:true}});tl.to({{}},{{duration:{TOTAL}}});window.__timelines["latest-captions"]=tl;</script></template>')
hosts.append(f'<div id="latest-captions" class="clip" data-composition-id="latest-captions" data-composition-src="compositions/latest-captions.html" data-start="0" data-duration="{TOTAL}" data-width="1920" data-height="1080" data-track-index="2" data-track-kind="captions" style="z-index:50;pointer-events:none"></div>')
root='''<!doctype html><html lang="en" data-composition-variables='[{"id":"repo","type":"string","label":"GitHub repository","default":"github.com/mrduhlol/Fetchly"}]'><head><meta charset="utf-8"><meta name="viewport" content="width=1920,height=1080"><title>Fetchly — Paste. Choose. Fetch.</title><link rel="stylesheet" href="assets/latest.css"><script src="assets/gsap.min.js"></script><script>window.__timelines={};</script></head><body>'''+f'<div id="root" data-composition-id="main" data-start="0" data-duration="{TOTAL}" data-width="1920" data-height="1080" data-fps="30">'+''.join(hosts)+''.join(audio)+f'</div><script>const tl=gsap.timeline({{paused:true}});tl.to({{}},{{duration:{TOTAL}}});window.__timelines.main=tl;</script></body></html>'
(P/'index.html').write_text(root)
(P/'fetchly-latest.srt').write_text('\n'.join(srt))
(P/'LATEST-STORYBOARD.md').write_text('# Current-UI promo\n\n'+ '\n'.join(f'- {a:.1f}–{a+d:.1f}s: {s[0]} — {s[2]}' for s,a,d in zip(SCENES,STARTS,DURATIONS))+'\n')
print(f'Built {TOTAL}s at 1920x1080 / 30fps, {cue_no-1} precisely timed sentence captions.')
