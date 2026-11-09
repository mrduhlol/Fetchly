"""Build the editable 64-second Fetchly film. All product labels trace to repo source."""
from pathlib import Path
import json
from html import escape
P = Path(__file__).resolve().parent
C = P/'compositions'
C.mkdir(exist_ok=True)
DURATION = 64
CURSOR = '<svg class="cursor" viewBox="0 0 64 78" aria-hidden="true"><path d="M5 3 L6 64 L22 49 L34 75 L46 69 L33 45 L56 44 Z" fill="#fff" stroke="#1b1915" stroke-width="3" stroke-linejoin="round"/></svg>'
def scene(name,duration,body,script,css=''):
    text = f'''<template>
<style>#root{{position:absolute;inset:0;overflow:hidden}}{css}</style>
<div id="root" data-composition-id="{name}" data-width="1920" data-height="1080" data-duration="{duration}">
<div id="{name}-clip" class="clip" data-start="0" data-duration="{duration}" data-track-index="1"><div id="{name}-stage" class="stage">{body}</div></div>
</div>
<script>{script}</script>
</template>'''
    (C/f'{name}.html').write_text(text)

def reg(name,code,tail=''):
    return f'''const tl=gsap.timeline({{paused:true}});{code}\ntl.to({{}},{{duration:.01}},{tail or '0'});window.__timelines["{name}"]=tl;'''

scene('pain',7,'''
<div class="pain-ground"></div>
<div class="safe-left"><p class="eyebrow" style="color:#9e563d">SOUND FAMILIAR?</p><h1 id="pain-title">An ad<br>first.</h1><h1 id="pain-quality">And this<br>quality?</h1></div>
<div id="pain-phone" class="phone-flat"><div class="screen">
<div class="fake-source"><div class="fake-thumb"><img src="assets/brand/temple.png" alt="Greek temple artwork"/></div><div class="fake-line"></div><div class="fake-line short"></div></div>
<div id="pain-gate"><p>ADVERTISEMENT</p><div class="ad-clock">00:30</div><div class="ad-track"><div id="pain-ad-bar"></div></div><div class="ad-disabled">Watch ad to continue</div></div>
<div id="pain-result"><div class="lowres"><img src="assets/brand/temple.png" alt="Illustrative low-quality result"/></div><strong>144p</strong><p>After all that waiting.</p></div>
</div></div>
<p class="pain-note">Illustrative download experience</p>
''',reg('pain', '''
tl.fromTo('#pain-phone',{x:70,rotation:5},{x:0,rotation:0,duration:.6,ease:'power3.out'},0);
tl.fromTo('#pain-ad-bar',{scaleX:.02},{scaleX:.17,duration:3.3,ease:'none'},0);
tl.fromTo('#pain-gate',{x:0},{x:-6,duration:.06,yoyo:true,repeat:3},1.15);
tl.fromTo('#pain-gate',{opacity:1},{opacity:0,duration:.15},3.25);
tl.fromTo('#pain-title',{opacity:1},{opacity:0,duration:.12},3.25);
tl.fromTo('#pain-quality',{opacity:0},{opacity:1,duration:.15},3.4);
tl.fromTo('#pain-result',{opacity:0,scale:1.15},{opacity:1,scale:1,duration:.3,ease:'power3.out'},3.4);
tl.fromTo('#pain-phone',{x:0},{x:12,duration:.05,yoyo:true,repeat:5},4.2);
tl.fromTo('#pain-stage',{opacity:1},{opacity:0,duration:.2},6.8);
''','6.98'),'''
.pain-ground{position:absolute;inset:0;background:linear-gradient(110deg,#d8d3c8,#bcb9b0)}#pain-stage{color:#38342e}.safe-left{top:245px}.safe-left h1{margin-top:36px;font-family:Manrope;font-size:130px;line-height:1.06;letter-spacing:-6px}#pain-quality{position:absolute;left:0;top:62px;opacity:0}#pain-phone{left:1170px;top:78px;box-shadow:30px 35px 50px #49443b44}.fake-source{position:absolute;inset:0;background:#efede7;padding:32px 20px}.fake-thumb{height:210px;overflow:hidden;background:#ccc}.fake-thumb img{width:100%;top:-80px!important}.fake-line{width:85%;height:15px;background:#c1beb5;margin-top:20px}.fake-line.short{width:55%}#pain-gate{position:absolute;inset:0;background:#171819ed;color:#eee;padding:240px 28px 0;text-align:center}#pain-gate p{font-size:22px;letter-spacing:3px;color:#bdbdb7}.ad-clock{font-size:88px;margin:35px 0}.ad-track{height:6px;background:#56534e;overflow:hidden}.ad-track div{height:100%;background:#d4ccbc;transform-origin:left}.ad-disabled{font-size:23px;border:1px solid #777;padding:18px 4px;margin-top:42px;color:#a3a19a}#pain-result{position:absolute;inset:0;background:#eee9df;text-align:center;padding-top:155px;color:#292722}.lowres{height:320px;overflow:hidden}.lowres img{position:static!important;width:100%;height:100%;object-fit:cover;image-rendering:pixelated}#pain-result strong{display:block;font-size:100px;margin:40px 0 12px}#pain-result p{font-size:27px}.pain-note{position:absolute;left:192px;bottom:110px;font-size:27px;color:#615a4e}
''')

scene('reveal',5,'''
<div class="stone stage"></div><img class="reveal-temple" src="assets/brand/monument.webp" alt="Fetchly Greek temple artwork"/>
<div id="reveal-lockup"><img src="assets/brand/fetchly-logo.png" alt="Fetchly Apollo icon"/><h1 data-var-text="appName">FETCHLY</h1><p id="reveal-line">Paste. Choose. Fetch.</p></div>
''',reg('reveal', '''
tl.fromTo('#reveal-lockup',{opacity:0,scale:.9},{opacity:1,scale:1,duration:.85,ease:'power2.out'},.1);
tl.fromTo('#reveal-line',{opacity:0},{opacity:1,duration:.45},2.3);
tl.fromTo('#reveal-lockup',{opacity:1},{opacity:0,duration:.25},4.7);
''','4.98'),'''.reveal-temple{position:absolute;width:1400px;left:260px;top:150px;opacity:.16}#reveal-lockup{position:absolute;inset:115px 0 0;text-align:center}#reveal-lockup>img{width:330px;height:330px;object-fit:contain}#reveal-lockup h1{font-size:135px;letter-spacing:14px;margin:18px 0}#reveal-line{font-size:34px;letter-spacing:6px;color:#ddc595}
''')

scene('beauty',9,'''
<div class="stone stage"></div><div id="beauty-canvas" class="canvas-stage" data-layout-ignore></div>
<div id="beauty-copy" class="safe-left"><p class="eyebrow gold">ON YOUR PHONE</p><h2 id="beauty-one">Paste.<br>Choose.<br><span class="gold">Fetch.</span></h2><h2 id="beauty-two">Every link.<br>A little<br><span class="gold">character.</span></h2><div class="rule"></div><p class="body-copy">The real Fetchly home screen</p></div>
''','''
window.__hf=window.__hf||{};window.__hf.buildReady=window.__hf.buildReady||{};
window.__hf.buildReady.beauty=(async()=>{
const stage=await window.Fetchly3D.create(document.getElementById('beauty-canvas'),'assets/screens/home-dark.png');
const clock={t:0};function draw(t){
 const smooth=x=>{x=Math.max(0,Math.min(1,x));return x*x*(3-2*x)};
 const p=smooth((t-2.7)/1.2),q=smooth((t-6)/1.0);
 stage.phone.position.set(2.2,0,0);stage.phone.rotation.set(.035,-.27+p*.14+q*.17,.025-q*.03);
 stage.camera.position.set(.3+p*1.0+q*.7,.3+p*1.8-q*3.1,14.8-p*4.1+q*.8);
 stage.camera.lookAt(0+p*.55+q*.5,p*1.25-q*2.0,0);stage.shadow.position.x=2.2;stage.draw();}
const tl=gsap.timeline({paused:true});tl.fromTo(clock,{t:0},{t:9,duration:9,ease:'none',onUpdate:()=>draw(clock.t)},0);
tl.fromTo('#beauty-copy',{opacity:0,x:-30},{opacity:1,x:0,duration:.45,ease:'power3.out'},.25);
tl.fromTo('#beauty-one',{opacity:1},{opacity:0,duration:.15},5.7);
tl.fromTo('#beauty-two',{opacity:0},{opacity:1,duration:.2},5.95);
tl.fromTo('#beauty-stage',{opacity:1},{opacity:0,duration:.16},8.83);
window.__timelines.beauty=tl;draw(0);
})();
''','''#beauty-copy{top:235px;width:710px}#beauty-copy h2{font-size:100px}#beauty-two{position:absolute;top:68px;opacity:0}#beauty-copy .body-copy{font-size:28px;margin-top:12px}
''')

names=[('youtube','YouTube','#d71924'),('instagram','Instagram','#a12e7d'),('tiktok','TikTok','#171717'),('x','X','#171717'),('reddit','Reddit','#e44c18'),('facebook','Facebook','#236ac1'),('pinterest','Pinterest','#b8172c'),('linkedin','LinkedIn','#236b91'),('youtube_music','YouTube<br>Music','#da163b')]
icons=''.join(f'<div id="platform-{i}" class="platform-item" style="left:{220+i*166}px"><div class="platform-disc" style="background:{color}"><img src="assets/platforms/{key}.svg" alt="{label.replace("<br>"," ")}"/></div><p>{label}</p></div>' for i,(key,label,color) in enumerate(names))
scene('platforms',6,f'''<div class="light-stone stage"></div><div class="platform-heading"><p class="eyebrow">BRING YOUR PUBLIC LINKS</p><h2>Your links.<br>Your platforms.</h2></div>{icons}<p class="platform-note">Public media · availability varies by platform</p>''',reg('platforms',''.join(f"tl.fromTo('#platform-{i}',{{opacity:0,scale:.25,y:60}},{{opacity:1,scale:1,y:0,duration:.3,ease:'back.out(1.35)'}},{.35+i*.45});\n" for i in range(9))+"tl.fromTo('.platform-heading',{opacity:0},{opacity:1,duration:.22},0);tl.fromTo('#platforms-stage',{opacity:1},{opacity:0,duration:.15},5.84);",'5.98'),'''.platform-heading{position:absolute;left:192px;top:150px}.platform-heading h2{font-size:104px;margin-top:24px}.platform-item{position:absolute;top:555px;width:155px;text-align:center;transform-origin:50% 60px}.platform-disc{width:116px;height:116px;border-radius:28px;margin:auto;display:grid;place-items:center;box-shadow:0 12px 20px #332b2022}.platform-disc img{width:75px;height:75px}.platform-item p{font-size:28px;line-height:1.3;margin-top:24px}.platform-note{position:absolute;left:192px;bottom:125px;font-size:30px;color:#60513b}
''')

scene('share',14,'''
<div class="stone stage"></div>
<div class="safe-left"><p class="eyebrow gold">THE SHARE SHORTCUT</p><h2>Share it.<br><span class="gold">Fetch it.</span></h2><div class="rule"></div><p id="share-caption" class="body-copy">Your download options.<br>Right over the app.</p></div>
<div id="share-phone" class="phone-flat"><div class="screen">
<div class="source-surface"><div class="source-brand"><img src="assets/platforms/youtube.svg" alt="YouTube"/>Public YouTube link</div><div class="source-thumb"><img src="assets/screens/quality-video.png" alt="Big Buck Bunny source thumbnail"/></div><h3>Big Buck Bunny</h3><p>Blender Foundation</p><div id="share-action">↗ &nbsp; Share</div></div>
<div id="share-picker"><p>Share with</p><div id="share-target"><img src="assets/brand/fetchly-logo.png" alt="Fetchly"/><strong>Fetchly</strong></div></div>
<div id="share-shade"></div><div id="share-dialog">
<h3>Download with Fetchly</h3><div class="dialog-thumb"><img src="assets/screens/quality-video.png" alt="Preview of Big Buck Bunny"/></div>
<p class="dialog-title">Big Buck Bunny 60fps 4K</p><p class="dialog-source">YouTube</p><div class="kind-row"><span class="selected">Video</span><span>Audio</span></div><div class="kind-row container-row"><span class="selected">MP4</span><span>MKV</span></div>
<div id="share-quality">2160p · MP4 · ~1.28 GB &nbsp;⌄</div><p class="source-note">Only formats available from the source are shown.</p>
<div class="dialog-actions"><span>Cancel</span><strong id="share-download">Download</strong></div></div>
</div></div>
<div id="share-tap" class="touch"></div><p class="share-disclosure">Share flow reconstructed from Fetchly’s native UI source</p>
''',reg('share','''
tl.fromTo('#share-phone',{x:100,opacity:0},{x:0,opacity:1,duration:.4,ease:'power3.out'},0);
tl.fromTo('#share-tap',{x:1438,y:520,opacity:0,scale:1.35},{opacity:1,scale:1,duration:.2},2.1);
tl.to('#share-tap',{opacity:0,duration:.25},2.45);
tl.fromTo('#share-picker',{y:370},{y:0,duration:.35,ease:'power3.out'},2.35);
tl.fromTo('#share-target',{scale:1},{scale:.93,duration:.1,yoyo:true,repeat:1},4.25);
tl.fromTo('#share-picker',{opacity:1},{opacity:0,duration:.15},4.5);
tl.fromTo('#share-shade',{opacity:0},{opacity:1,duration:.2},4.5);
tl.fromTo('#share-dialog',{opacity:0,y:40},{opacity:1,y:0,duration:.3,ease:'power3.out'},4.6);
tl.fromTo('#share-phone',{scale:1,x:0,y:0},{scale:1.18,x:-50,y:30,duration:.7,ease:'power3.inOut'},7);
tl.fromTo('#share-quality',{backgroundColor:'#28241e'},{backgroundColor:'#5a492b',duration:.15,yoyo:true,repeat:1},9.3);
tl.fromTo('#share-download',{scale:1},{scale:.94,duration:.11,yoyo:true,repeat:1},11.5);
tl.fromTo('#share-stage',{opacity:1},{opacity:0,duration:.2},13.8);
''','13.98'),'''
#share-phone{width:510px;height:946px;left:1160px;top:67px;transform-origin:50% 45%}.source-surface{position:absolute;inset:0;background:#f5f1e8;color:#24211b;padding:36px 23px}.source-brand{font-size:25px;display:flex;align-items:center;gap:15px}.source-brand img{position:static!important;width:45px!important;height:35px;filter:brightness(.3)}.source-thumb{height:240px;overflow:hidden;position:relative;margin:30px -23px 22px}.source-thumb img{width:100%;top:-162px!important}.source-surface h3{font-size:34px}.source-surface p{font-size:25px;margin-top:12px;color:#686359}#share-action{margin-top:35px;background:#e1dccf;display:inline-block;padding:14px 26px;border-radius:28px;font-size:30px}#share-picker{position:absolute;left:0;bottom:0;width:100%;height:350px;padding:30px;background:#25241f;border-radius:28px 28px 0 0;color:#eee6d8}#share-picker>p{font-size:29px}#share-target{width:140px;text-align:center;margin:35px auto}#share-target img{position:static!important;width:85px!important;height:85px;display:block;margin:auto}#share-target strong{font-size:27px}#share-shade{position:absolute;inset:0;background:#0008;opacity:0}#share-dialog{position:absolute;left:15px;top:165px;width:452px;padding:23px;background:#181916;border-radius:25px;color:#ece5d9;opacity:0;box-shadow:0 15px 45px #0006}#share-dialog h3{font-size:29px;font-weight:600;line-height:1.3}.dialog-thumb{height:167px;overflow:hidden;position:relative;border-radius:12px;margin:20px 0 16px}.dialog-thumb img{top:-142px!important}.dialog-title{font-size:26px;line-height:1.3}.dialog-source{font-size:24px;color:#b8ad9a;margin:12px 0}.kind-row{display:flex;gap:10px;margin:14px 0}.kind-row span{font-size:25px;padding:8px 17px;border:1px solid #92826b;border-radius:14px}.kind-row span.selected{background:#4b3d2a;color:#f2deb9}.container-row span{font-size:23px}#share-quality{padding:19px 12px;font-size:24px;border:1px solid #ad9774;border-radius:12px;margin:20px 0}.source-note{font-size:20px;color:#bcb4a4;line-height:1.4}.dialog-actions{display:flex;align-items:center;justify-content:flex-end;gap:20px;font-size:23px;margin-top:24px}.dialog-actions strong{display:block;background:#d9b873;color:#302516;padding:14px 19px;clip-path:polygon(5% 0,95% 0,100% 16%,100% 84%,95% 100%,5% 100%,0 84%,0 16%)}.share-disclosure{position:absolute;left:192px;bottom:110px;font-size:27px;color:#b6ae9e;max-width:780px;line-height:1.4}
''')

scene('features',7,'''
<div class="light-stone stage"></div>
<div id="feature-a"><div class="safe-left"><p class="eyebrow">MAKE IT YOURS</p><h2>Video.<br>Audio.<br><span style="color:#8e652c">Your choice.</span></h2></div><div class="feature-quality screen-crop"><img src="assets/screens/quality-video.png" alt="Actual Fetchly video and audio format controls"/></div></div>
<div id="feature-b"><h2>More than<br>one link?</h2><div class="batch-crop screen-crop"><img src="assets/screens/home-dark.png" alt="Actual Add multiple links action"/></div></div>
<div id="feature-c"><h2>Pause.<br><span style="color:#8e652c">Resume.</span></h2><div class="resume-track"><div id="feature-progress"></div><div class="resume-symbol">Ⅱ</div></div><p>When supported by the source</p></div>
''',reg('features','''
tl.fromTo('#feature-a',{opacity:0},{opacity:1,duration:.2},0);
tl.fromTo('.feature-quality',{scale:1.05},{scale:1,duration:.5,ease:'power2.out'},0);
tl.fromTo('#feature-a',{opacity:1},{opacity:0,duration:.15},2.1);
tl.fromTo('#feature-b',{opacity:0,x:60},{opacity:1,x:0,duration:.3,ease:'power3.out'},2.25);
tl.fromTo('#feature-b',{opacity:1},{opacity:0,duration:.15},4.1);
tl.fromTo('#feature-c',{opacity:0},{opacity:1,duration:.25},4.3);
tl.fromTo('#feature-progress',{scaleX:.25},{scaleX:.56,duration:.5,ease:'none'},4.5);
tl.to('#feature-progress',{scaleX:.56,duration:.5},5);
tl.to('#feature-progress',{scaleX:.85,duration:.8,ease:'none'},5.5);
tl.fromTo('#features-stage',{opacity:1},{opacity:0,duration:.15},6.84);
''','6.98'),'''
#feature-a,#feature-b,#feature-c{position:absolute;inset:0}.feature-quality{left:1030px;top:205px;width:660px;height:650px;border-radius:25px}.feature-quality img{top:-910px}#feature-a .safe-left{top:220px}#feature-b,#feature-c{opacity:0}#feature-b h2{position:absolute;left:192px;top:190px;font-size:110px}.batch-crop{left:900px;top:395px;width:800px;height:220px;border-radius:24px}.batch-crop img{top:-610px}#feature-c h2{position:absolute;left:192px;top:240px;font-size:120px}.resume-track{position:absolute;left:1030px;top:480px;width:620px;height:20px;background:#a89a8066}.resume-track>div:first-child{height:100%;background:#8e652c;transform-origin:left}.resume-symbol{position:absolute;left:250px;top:-150px;font-size:100px;color:#8e652c}#feature-c>p{position:absolute;left:1030px;top:580px;font-size:29px;color:#66573e}
''')

scene('touch',7.2,'''
<div class="stone stage"></div><div id="touch-canvas" class="canvas-stage" data-layout-ignore></div><div id="touch-copy"><p class="eyebrow gold">PASTE. CHOOSE.</p><h2>FETCH.</h2></div><div id="touch-black"></div>
''','''
window.__hf=window.__hf||{};window.__hf.buildReady=window.__hf.buildReady||{};
window.__hf.buildReady.touch=(async()=>{
const s=await window.Fetchly3D.create(document.getElementById('touch-canvas'),'assets/screens/home-enabled.png',{hand:true});
const clock={t:0};function render(t){const sm=x=>{x=Math.max(0,Math.min(1,x));return x*x*(3-2*x)};const orbit=sm((t-.4)/1.9),dive=sm((t-2.25)/1.5),reach=sm((t-4.2)/2.42);
s.phone.position.set(0,0,0);s.phone.rotation.set(0,0,0);
s.camera.position.set(orbit*4.2-dive*2.3,.4+dive*.34,14.8-orbit*3.1-dive*7.7);
s.camera.lookAt(0,dive*.74,0);
s.hand.position.set(5.5*(1-reach),-.5*(1-reach),3.2*(1-reach));s.hand.visible=t>=4.1;
// A 0.1s press is visible before the blackout; the fingertip lands on the screen.
const press=Math.max(0,1-Math.abs((t-6.66)/.1));s.phone.position.z=-.035*press;s.hand.position.z-=.16*press;s.draw();}
const tl=gsap.timeline({paused:true});tl.fromTo(clock,{t:0},{t:7.2,duration:7.2,ease:'none',onUpdate:()=>render(clock.t)},0);
tl.fromTo('#touch-copy',{opacity:0},{opacity:1,duration:.3},.2);tl.fromTo('#touch-copy',{opacity:1},{opacity:0,duration:.3},2);
tl.fromTo('#touch-black',{opacity:0},{opacity:1,duration:.1},6.83);
window.__timelines.touch=tl;render(0);
})();
''','''#touch-copy{position:absolute;left:192px;top:250px}#touch-copy h2{font-size:125px;margin-top:25px}#touch-black{position:absolute;inset:0;background:#000;opacity:0}
''')

scene('endcard',8.8,f'''
<div class="end-ground"></div><canvas id="end-smoke" width="960" height="540" data-layout-ignore></canvas>
<div id="end-lockup"><img id="end-logo" src="assets/brand/fetchly-logo.png" alt="Fetchly Apollo icon"/><h1 data-var-text="creator">@mrduhlol</h1><p id="end-repo" data-var-text="repo">github.com/mrduhlol/Fetchly</p><p id="end-prompt">Download Fetchly on GitHub</p><img id="end-download" src="assets/brand/download-apk.svg" alt="Download Fetchly for Android — original README button"/></div>
<div id="end-cursor">{CURSOR}</div><div id="end-black"></div>
''','''
const clock={t:0},canvas=document.getElementById('end-smoke'),ctx=canvas.getContext('2d');
function smoke(t){ctx.clearRect(0,0,960,540);const envelope=Math.min(1,Math.max(0,(t-.35)/1.0))*Math.min(1,Math.max(0,(8.7-t)/.5));for(let i=0;i<26;i++){let side=i%2===0?-1:1;let x=480+side*(310-(t*.055+i*.018)%1*230)+Math.sin(i*2.37+t*.5)*70;let y=460-i*12-Math.sin(i*1.7+t*.4)*60;let r=110+(i%5)*17;let g=ctx.createRadialGradient(x,y,0,x,y,r);g.addColorStop(0,'rgba(169,153,121,'+(.055*envelope)+')');g.addColorStop(.5,'rgba(108,105,98,'+(.025*envelope)+')');g.addColorStop(1,'rgba(40,40,36,0)');ctx.fillStyle=g;ctx.beginPath();ctx.arc(x,y,r,0,Math.PI*2);ctx.fill();}}
const tl=gsap.timeline({paused:true});tl.fromTo(clock,{t:0},{t:8.8,duration:8.8,ease:'none',onUpdate:()=>smoke(clock.t)},0);
tl.fromTo('#end-lockup',{opacity:0},{opacity:1,duration:.8},.8);
tl.fromTo('#end-repo',{opacity:0},{opacity:1,duration:.55},1.4);
tl.fromTo('#end-prompt',{opacity:0},{opacity:1,duration:.4},1.9);
tl.fromTo('#end-download',{opacity:0,y:20},{opacity:1,y:0,duration:.4,ease:'power2.out'},2.1);
tl.fromTo('#end-cursor',{x:1650,y:1040,opacity:0},{x:1090,y:809,opacity:1,duration:.8,ease:'power3.out'},7.0);
tl.fromTo('#end-download',{scale:1},{scale:.965,duration:.1,yoyo:true,repeat:1},8.05);
tl.fromTo('#end-cursor .cursor',{scale:1},{scale:.92,duration:.1,yoyo:true,repeat:1},8.05);
tl.fromTo('#end-black',{opacity:0},{opacity:1,duration:.12},8.63);
window.__timelines.endcard=tl;smoke(0);
''','''
.end-ground{position:absolute;inset:0;background:#050504}#end-smoke{position:absolute;width:1920px;height:1080px;left:0;top:0}#end-lockup{position:absolute;inset:0;text-align:center}#end-logo{position:absolute;width:190px;height:190px;object-fit:contain;left:865px;top:125px}#end-lockup h1{position:absolute;top:345px;left:192px;width:1536px;font-size:115px;letter-spacing:-2px;color:#e4c68a}#end-repo{position:absolute;top:505px;left:192px;width:1536px;font-size:48px;color:#eee7d9}#end-prompt{position:absolute;top:605px;left:192px;width:1536px;font-size:29px;color:#bbb19e}#end-download{position:absolute;left:624px;top:705px;width:672px;height:176px}#end-cursor{position:absolute;left:0;top:0}#end-black{position:absolute;inset:0;background:#000;opacity:0}
''')

# Editable root: graphics on one lane, voice/music/SFX separated by role.
shots=[('pain',0,7),('reveal',7,5),('beauty',12,9),('platforms',21,6),('share',27,14),('features',41,7),('touch',48,7.2),('endcard',55.2,8.8)]
hosts='\n'.join(f'<div id="{name}" class="clip" data-composition-id="{name}" data-composition-src="compositions/{name}.html" data-start="{start}" data-duration="{duration}" data-width="1920" data-height="1080" data-track-index="1" data-track-kind="graphics"></div>' for name,start,duration in shots)
tracks=[]
def audio(id,src,start,duration,volume,track,offset=0,fadein=0,fadeout=0):
    tracks.append(f'<audio id="{id}" class="clip" src="{src}" data-start="{start:.4f}" data-duration="{duration:.4f}" data-media-start="{offset:.4f}" data-volume="{volume}" data-fade-in="{fadein}" data-fade-out="{fadeout}" data-track-index="{track}" data-audio-group="'+('voiceover' if track==2 else 'music' if track in (3,4) else 'sfx')+'"></audio>')
voices=json.loads((P/'narration.json').read_text())
starts={'frustration':.7,'reveal':8.0,'home':12.5,'platforms':21.65,'share':28.1,'features':41.05}
for v in voices:audio('vo-'+v['id'],v['path'],starts[v['id']],v['duration'],.86,2)
audio('music-lyre','assets/audio/pixabay/lyre.mp3',7.25,40.75,.18,3,offset=9,fadein=.8,fadeout=.9)
audio('music-rhythm','assets/audio/pixabay/rhythm.mp3',12,42.8,.13,4,offset=3,fadein=1.5,fadeout=.6)
audio('music-outro','assets/audio/pixabay/lyre.mp3',56.1,7.65,.15,3,offset=51,fadein=1.2,fadeout=.55)
# Match each supplied effect's measured peak to visible contact (+ one frame).
for i,t in enumerate([1.2,2.0,4.25,29.35,31.25,38.5,63.25]):audio('click-'+str(i),'assets/audio/pixabay/click.mp3',t-.074+.033,.24,.55,5+i%2)
for i in range(9):audio('platform-pop-'+str(i),'assets/audio/pixabay/pop.mp3',21+.35+i*.45-.202+.033,.65,.31,7+i%2)
for i,t in enumerate([7.0,12.0,21.0,27.0,41.0,48.0]):audio('transition-'+str(i),'assets/audio/pixabay/whoosh.mp3',max(0,t-.45),.85,.15,9,offset=1.65,fadein=.08,fadeout=.18)
audio('final-riser','assets/audio/pixabay/whoosh.mp3',49.9,4.9,.38,10,offset=0,fadein=.4,fadeout=.25)
audio('final-impact','assets/audio/pixabay/impact.mp3',54.65-1.815+.033,3.25,.65,11,offset=0,fadeout=.55)
html='''<!doctype html><html lang="en" data-composition-variables='[{"id":"appName","type":"string","label":"App name","default":"FETCHLY"},{"id":"creator","type":"string","label":"Creator","default":"@mrduhlol"},{"id":"repo","type":"string","label":"Repository","default":"github.com/mrduhlol/Fetchly"}]'><head><meta charset="UTF-8"><meta name="viewport" content="width=1920,height=1080"><title>Fetchly — Keep the Moment</title><link rel="stylesheet" href="assets/style.css"><script src="assets/gsap.min.js"></script><script>window.__timelines=window.__timelines||{};window.__threeReady=import('./assets/three.module.js');</script><script src="assets/phone3d.js"></script><style>#root{position:absolute;inset:0;width:1920px;height:1080px}.clip{position:absolute;inset:0}</style></head><body><div id="root" data-composition-id="main" data-start="0" data-duration="64" data-width="1920" data-height="1080" data-fps="30">'''+hosts+'\n'+'\n'.join(tracks)+'''</div><script>const tl=gsap.timeline({paused:true});tl.to({},{duration:64});window.__timelines.main=tl;</script></body></html>'''
(P/'index.html').write_text(html)
(P/'STORYBOARD.md').write_text('# Fetchly — 64-second storyboard\n\n'+'\n'.join(f'- {start:.1f}–{start+duration:.1f}s: {name}' for name,start,duration in shots)+'\n\nThree.js phone and hand are original local geometry. Share UI is a faithful code-derived reconstruction, disclosed on screen. All other product screens are actual Android captures. No stock video clips are used.\n')
print('Built eight editable scenes, 64 seconds, separate voice/music/SFX tracks.')
