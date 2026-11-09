/* Original deterministic Three.js product stage. No stock video, animation loops or network assets. */
window.Fetchly3D = {
  async create(container, source, options = {}) {
    const T = await window.__threeReady;
    const scene = new T.Scene();
    const renderer = new T.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true });
    renderer.setSize(1920, 1080); renderer.setPixelRatio(1);
    renderer.outputColorSpace = T.SRGBColorSpace;
    renderer.toneMapping = T.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.25;
    container.appendChild(renderer.domElement);
    const camera = new T.PerspectiveCamera(37, 1920/1080, .1, 100);
    camera.position.set(0, .4, 14.8); camera.lookAt(0,0,0);
    scene.add(new T.HemisphereLight(0xfff1d5,0x39332a,3.0));
    const key = new T.DirectionalLight(0xffe1ac,4.0); key.position.set(-5,7,8); scene.add(key);
    const rim = new T.DirectionalLight(0xe5e5f0,3.0); rim.position.set(6,3,-4); scene.add(rim);
    const fill = new T.DirectionalLight(0xffffff,1.0); fill.position.set(2,0,9); scene.add(fill);
    const bronze = new T.MeshStandardMaterial({color:0x8c7048,metalness:.82,roughness:.3});
    const dark = new T.MeshStandardMaterial({color:0x171713,metalness:.52,roughness:.38});
    const glass = new T.MeshStandardMaterial({color:0x060708,metalness:.1,roughness:.18});
    function shape(w,h,r){const s=new T.Shape();s.moveTo(-w/2+r,-h/2);s.lineTo(w/2-r,-h/2);s.quadraticCurveTo(w/2,-h/2,w/2,-h/2+r);s.lineTo(w/2,h/2-r);s.quadraticCurveTo(w/2,h/2,w/2-r,h/2);s.lineTo(-w/2+r,h/2);s.quadraticCurveTo(-w/2,h/2,-w/2,h/2-r);s.lineTo(-w/2,-h/2+r);s.quadraticCurveTo(-w/2,-h/2,-w/2+r,-h/2);return s;}
    const phone=new T.Group(); scene.add(phone);
    const shell=new T.Mesh(new T.ExtrudeGeometry(shape(3.64,7.72,.32),{depth:.22,bevelEnabled:true,bevelSegments:3,steps:1,bevelSize:.055,bevelThickness:.035,curveSegments:12}),bronze);shell.position.z=-.15;phone.add(shell);
    const bezel=new T.Mesh(new T.ShapeGeometry(shape(3.53,7.61,.29),16),glass);bezel.position.z=.115;phone.add(bezel);
    const image = new Image(); image.src=source; await image.decode();
    const c=document.createElement('canvas');c.width=720;c.height=1460;
    c.getContext('2d').drawImage(image,0,48,720,1460,0,0,720,1460);
    const texture=new T.CanvasTexture(c);texture.colorSpace=T.SRGBColorSpace;texture.anisotropy=4;
    const geometry=new T.ShapeGeometry(shape(3.4,7.35,.23),20);
    const positions=geometry.attributes.position; const uv=geometry.attributes.uv;
    for(let i=0;i<positions.count;i++)uv.setXY(i,(positions.getX(i)+1.7)/3.4,(positions.getY(i)+3.675)/7.35);
    const screen=new T.Mesh(geometry,new T.MeshBasicMaterial({map:texture,toneMapped:false}));screen.position.z=.13;phone.add(screen);
    const lens=new T.Mesh(new T.CylinderGeometry(.055,.055,.014,20),dark);lens.rotation.x=Math.PI/2;lens.position.set(0,3.57,.146);phone.add(lens);
    for(const [y,h] of [[1.5,.68],[.45,.44]]){const button=new T.Mesh(new T.BoxGeometry(.055,h,.11),bronze);button.position.set(1.875,y,0);phone.add(button);}
    const shadowCanvas=document.createElement('canvas');shadowCanvas.width=256;shadowCanvas.height=256;const ctx=shadowCanvas.getContext('2d');let g=ctx.createRadialGradient(128,128,8,128,128,122);g.addColorStop(0,'rgba(0,0,0,.65)');g.addColorStop(1,'rgba(0,0,0,0)');ctx.fillStyle=g;ctx.fillRect(0,0,256,256);
    const shadow=new T.Mesh(new T.PlaneGeometry(7,3),new T.MeshBasicMaterial({map:new T.CanvasTexture(shadowCanvas),transparent:true,depthWrite:false}));shadow.rotation.x=-Math.PI/2;shadow.position.set(0,-4.2,0);scene.add(shadow);
    let hand;
    if(options.hand){
      hand=new T.Group();scene.add(hand);
      // A quiet, deterministic stone grain gives the sculpture depth without external textures.
      const stoneCanvas=document.createElement('canvas'); stoneCanvas.width=256; stoneCanvas.height=256;
      const stoneCtx=stoneCanvas.getContext('2d'); const pixels=stoneCtx.createImageData(256,256);
      for(let y=0;y<256;y++) for(let x=0;x<256;x++){
        const vein=Math.pow(Math.max(0,Math.sin(x*.055+y*.027+2.2*Math.sin(y*.025)+.7*Math.sin(x*.035+y*.018))),18);
        const grain=Math.sin(x*1.73+y*2.19)*Math.sin(x*.39-y*.71);
        const shade=vein*13+grain*2, i=(y*256+x)*4;
        pixels.data[i]=235-shade; pixels.data[i+1]=229-shade; pixels.data[i+2]=216-shade; pixels.data[i+3]=255;
      }
      stoneCtx.putImageData(pixels,0,0);
      const stoneTexture=new T.CanvasTexture(stoneCanvas); stoneTexture.colorSpace=T.SRGBColorSpace;
      const marble=new T.MeshStandardMaterial({map:stoneTexture,roughness:.48,metalness:.015});
      const nail=new T.MeshStandardMaterial({color:0xf0e7d9,roughness:.42,metalness:.01});
      const sphereGeometry=new T.SphereGeometry(1,32,24);
      function ellipsoid(x,y,z,sx,sy,sz,material=marble){const m=new T.Mesh(sphereGeometry,material);m.position.set(x,y,z);m.scale.set(sx,sy,sz);hand.add(m);return m;}
      function bone(a,b,r1,r2){let va=new T.Vector3(...a),vb=new T.Vector3(...b),d=vb.clone().sub(va);let m=new T.Mesh(new T.CylinderGeometry(r2,r1,d.length(),24),marble);m.position.copy(va.clone().add(vb).multiplyScalar(.5));m.quaternion.setFromUnitVectors(new T.Vector3(0,1,0),d.normalize());hand.add(m);ellipsoid(...a,r1,r1,r1);ellipsoid(...b,r2,r2,r2);}
      // Extended index, three curled fingers, thumb, palm and forearm form one carved hand.
      ellipsoid(2.9,-.75,1.15,.74,.87,.33).rotation.z=-.64;
      ellipsoid(2.54,-.42,1.13,.53,.55,.30).rotation.z=-.64;
      bone([3.1,-1.18,1.15],[4.7,-2.5,1.55],.43,.48);
      bone([2.65,-.07,1.05],[1.63,.39,.67],.25,.205);
      bone([1.63,.39,.67],[.68,.68,.34],.205,.17);
      bone([.68,.68,.34],[0,.74,.30],.17,.145);
      const n=ellipsoid(.21,.78,.423,.19,.08,.028,nail);n.rotation.z=.08;
      for(let i=0;i<3;i++){
        const y=-.43-i*.42,x=2.45+i*.17;
        bone([x,y,1.12],[x-.62,y+.17,1.47],.235-i*.025,.205-i*.02);
        bone([x-.62,y+.17,1.47],[x-.78,y-.16,1.68],.205-i*.02,.18-i*.02);
        bone([x-.78,y-.16,1.68],[x-.40,y-.34,1.64],.18-i*.02,.15-i*.02);
      }
      bone([2.54,-1.14,1.21],[1.68,-1.09,1.22],.28,.22);
      bone([1.68,-1.09,1.22],[1.30,-.58,1.29],.22,.18);
      const cuff=new T.Mesh(new T.CylinderGeometry(.48,.49,.20,32),bronze);cuff.position.set(3.65,-1.64,1.3);cuff.rotation.z=-.86;hand.add(cuff);
    }
    function draw(){renderer.render(scene,camera);}
    return {T,scene,renderer,camera,phone,hand,shadow,draw};
  }
};
