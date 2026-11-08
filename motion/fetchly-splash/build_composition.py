from pathlib import Path
P=Path(__file__).parent; A=P/'assets'
word=(A/'fetchly-wordmark.svg').read_text().replace('<svg ','<svg id="word-svg" ',1)
laurel=(A/'laurel-divider.svg').read_text().replace('<svg ','<svg id="laurel-svg" ',1)
(P/'index.html').write_text('''<!doctype html><html lang="en"><head><meta charset="utf-8"><title>FETCHLY · Apollo</title><script src="assets/gsap.min.js"></script><style>*{box-sizing:border-box;margin:0}html,body{width:2160px;height:3840px;overflow:hidden;background:#000}#root{position:relative;width:2160px;height:3840px}</style></head><body><div id="root" data-composition-id="main" data-start="0" data-duration="6.6" data-width="2160" data-height="3840"><div id="splash" class="clip" data-composition-id="splash" data-composition-src="compositions/splash.html" data-start="0" data-duration="6.6" data-width="2160" data-height="3840" data-track-index="0" data-track-kind="graphics"></div></div><script>window.__timelines=window.__timelines||{};window.__timelines.main=gsap.timeline({paused:true});</script></body></html>''')
(P/'compositions/splash.html').write_text('''<template><style>
#root{position:absolute;inset:0;background:#000;--gold:#d3b372}
.clip{position:absolute;inset:0}.plate,.rays,.dust{position:absolute;width:2160px;height:3840px;object-fit:contain}.plate{opacity:0}.rays,.dust{opacity:0}
#composition{position:absolute;inset:0}.hero{position:absolute;left:470px;top:760px;width:1220px;height:1220px;opacity:0;mask-image:linear-gradient(to bottom,#000 0%,#000 76%,transparent 100%)}.hero img{width:100%;height:100%;object-fit:contain}
.hero-shade{position:absolute;inset:0;background:linear-gradient(0deg,#0c0b08 0%,transparent 21%);pointer-events:none}
.word{position:absolute;left:280px;top:2200px;width:1600px;height:300px}.word svg{width:1600px;height:300px;overflow:visible}.word svg g[id^="letter"]{opacity:0}.sweep{position:absolute;inset:0;opacity:0;background:linear-gradient(110deg,transparent calc(var(--shimmer-pos,-20%) - 12%),#ffe1a1 var(--shimmer-pos,-20%),transparent calc(var(--shimmer-pos,-20%) + 12%));mask:url('assets/fetchly-wordmark.svg') center/contain no-repeat;mix-blend-mode:screen}
.laurel{position:absolute;left:530px;top:2570px;width:1100px;height:260px}.laurel svg{width:1100px;height:260px}.leaf{opacity:0}.stem{stroke-dasharray:550;stroke-dashoffset:550}
.laurel-shimmer{position:absolute;inset:0;opacity:0;background:linear-gradient(100deg,transparent calc(var(--shimmer-pos,-20%) - 15%),#ffe5aa var(--shimmer-pos,-20%),transparent calc(var(--shimmer-pos,-20%) + 15%));mask:url('assets/laurel-divider.svg') center/contain no-repeat;mix-blend-mode:screen}
.loading{position:absolute;left:700px;top:2990px;width:760px;height:20px;opacity:0}.track{position:absolute;top:9px;width:760px;height:3px;background:#b1945438}.fill{position:absolute;top:9px;width:760px;height:3px;transform-origin:left;background:linear-gradient(90deg,#92733b,#d8bb76,#ffedb7)}.highlight{position:absolute;left:0;top:0;width:50px;height:20px;background:radial-gradient(ellipse,#fff3c2 0%,#dbb97266 18%,transparent 68%);opacity:0}
</style><div id="root" data-composition-id="splash" data-width="2160" data-height="3840"><div id="composition">
<div id="background" class="clip" data-start="0" data-duration="6.6" data-track-index="0"><img class="plate" src="assets/background.svg" alt=""/></div>
<div id="atmosphere" class="clip" data-start="0" data-duration="6.6" data-track-index="1"><img class="rays" src="assets/golden-light-rays.svg" alt=""/></div>
<div id="apollo" class="clip" data-start="0" data-duration="6.6" data-track-index="2"><div class="hero"><img src="assets/apollo.png" alt="Original Apollo sculpture with sun rays, arrows and drapery"/></div></div>
<div id="particles" class="clip" data-start="0" data-duration="6.6" data-track-index="3"><img class="dust" src="assets/gold-particles.svg" alt=""/></div>
<div id="wordmark" class="clip" data-start="0" data-duration="6.6" data-track-index="4"><div class="word" aria-label="FETCHLY">'''+word+'''<div class="sweep"></div></div></div>
<div id="divider" class="clip" data-start="0" data-duration="6.6" data-track-index="5"><div class="laurel">'''+laurel+'''<div class="laurel-shimmer"></div></div></div>
<div id="progress" class="clip" data-start="0" data-duration="6.6" data-track-index="6"><div class="loading"><div class="track"></div><div class="fill"></div><div class="highlight"></div></div></div>
</div></div><script>
const tl=gsap.timeline({paused:true});
tl.fromTo('.plate',{opacity:0},{opacity:1,duration:1.5,ease:'power2.inOut'},0.05);
tl.fromTo('.rays',{opacity:0},{opacity:1,duration:1.7,ease:'power2.inOut'},0.15);
tl.fromTo('.hero',{opacity:0,y:240},{opacity:1,y:0,duration:1.85,ease:'power4.out'},0.15);
tl.fromTo('.dust',{opacity:0,y:40},{opacity:.6,y:-40,duration:5.5,ease:'none'},0.5);
tl.fromTo('[id^="letter-"]',{opacity:0},{opacity:1,duration:.48,stagger:.085,ease:'power2.inOut'},2);
tl.fromTo('.sweep',{opacity:0,'--shimmer-pos':'-20%'},{opacity:.7,'--shimmer-pos':'120%',duration:.94,ease:'power2.inOut'},2.2);
tl.to('.sweep',{opacity:0,duration:.12},3.18);
tl.fromTo('.stem',{strokeDashoffset:550},{strokeDashoffset:0,duration:.72,ease:'power2.out'},3.2);
for(let i=0;i<9;i++)tl.fromTo('.leaf-'+i,{opacity:0},{opacity:1,duration:.25,ease:'power2.out'},3.24+i*.065);
tl.fromTo('.laurel-shimmer',{opacity:0,'--shimmer-pos':'-20%'},{opacity:.45,'--shimmer-pos':'120%',duration:.40,ease:'power2.inOut'},3.73);
tl.to('.laurel-shimmer',{opacity:0,duration:.1},4.15);
tl.fromTo('.loading',{opacity:0},{opacity:1,duration:.24,ease:'power2.out'},4.2);
tl.fromTo('.fill',{scaleX:0},{scaleX:1,duration:1.63,ease:'power1.inOut'},4.25);
tl.fromTo('.highlight',{x:-25,opacity:0},{x:735,opacity:.85,duration:1.63,ease:'power1.inOut'},4.25);
tl.to('.highlight',{opacity:0,duration:.15},5.9);
tl.fromTo('#composition',{opacity:1},{opacity:0,duration:.55,ease:'power2.inOut'},6);
window.__timelines=window.__timelines||{};window.__timelines.splash=tl;
</script></template>''')
