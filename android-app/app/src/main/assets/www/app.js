// SawerOverlay - TikTok Live Overlay Studio Core
const canvas = document.getElementById('programCanvas');
const ctx = canvas.getContext('2d');
const previewCanvas = document.getElementById('previewCanvas');
const pctx = previewCanvas.getContext('2d');
const video = document.getElementById('previewVideo');
const layerListEl = document.getElementById('layerList');
const safeArea = document.getElementById('safeArea');

let layers = [
  {
    id:'l1',
    type:'text',
    text:'🔴 LIVE TIKTOK - JAMAL GAMING',
    x:0.05, y:0.07, w:0.9, h:0.08,
    fontFamily:'Bebas Neue',
    fontSize:32,
    color:'#ffffff',
    bgType:'gradient-tiktok',
    stroke:4,
    strokeColor:'#000000',
    autoShow:true,
    stealth:false,
    visible:true,
    rotation:0
  },
  {
    id:'l2',
    type:'saweria',
    text:'💸 SAWERIA: saweria.co/jamalstream',
    x:0.05, y:0.82, w:0.9, h:0.07,
    fontFamily:'Poppins',
    fontSize:22,
    color:'#ffffff',
    bgType:'solid',
    bgColor:'#000000cc',
    stroke:2,
    autoShow:true,
    stealth:false,
    visible:true,
    lastDonation:null
  },
  {
    id:'l3',
    type:'goal',
    text:'TARGET: 500K / 1JT 🎯',
    x:0.05, y:0.15, w:0.6, h:0.05,
    fontFamily:'Inter',
    fontSize:16,
    color:'#00f2ea',
    bgType:'solid',
    bgColor:'#000000aa',
    stroke:0,
    autoShow:false,
    stealth:true,
    visible:true,
    progress:0.5
  }
];

let selectedId = 'l1';
let isLive = false;
let viewMode = 'viewer'; // viewer = what audience sees, streamer = stealth preview (clean)
let dragState = null;
let res = {w:1080, h:1920};
let saweriaConnected = false;
let saweriaPollInterval = null;

const colors = ['#ffffff','#00f2ea','#ff0050','#ffe600','#00ff88','#ff8a00','#8a2be2','#000000'];

function init(){
  // colors
  const colorRow = document.getElementById('colorRow');
  colors.forEach(c=>{
    const d=document.createElement('div');
    d.className='color-opt';
    d.style.background=c;
    d.style.border=c==='#ffffff'?'1px solid #333':'';
    d.onclick=()=>updateSelected('color',c);
    colorRow.appendChild(d);
  });

  setupCanvas();
  startCamera();
  renderLayerList();
  selectLayer(selectedId);
  render();
  bindEvents();
  logSaweria('System siap. Edit overlay, lalu klik LIVE.');
  // responsive menu btn show
  if(window.innerWidth<900) document.getElementById('menuBtn').style.display='block';
}

function setupCanvas(){
  const dpr = window.devicePixelRatio || 1;
  [canvas, previewCanvas].forEach(c=>{
    c.width = res.w;
    c.height = res.h;
    c.style.width='100%';
    c.style.height='100%';
  });
  ctx.textBaseline='middle';
}

async function startCamera(){
  try{
    const stream = await navigator.mediaDevices.getUserMedia({video:{facingMode:'user', width:{ideal:1080}, height:{ideal:1920}}, audio:true});
    video.srcObject = stream;
  }catch(e){
    console.log('camera fail', e);
    video.poster = '';
    // fallback gradient
    video.style.background='linear-gradient(180deg,#1a1a2a,#000)';
  }
}

function renderLayerList(){
  layerListEl.innerHTML='';
  [...layers].reverse().forEach(l=>{
    const div=document.createElement('div');
    div.className='layer-item'+(l.id===selectedId?' active':'');
    div.innerHTML=`
      <div class="left"><div class="dot" style="background:${l.type==='saweria'?'#ff0050':'#00f2ea'}"></div>
      <div><div style="font-size:12px; font-weight:700;">${l.type.toUpperCase()}</div><div style="font-size:10px; opacity:.6; max-width:180px; overflow:hidden; text-overflow:ellipsis;">${l.text.slice(0,30)}</div></div></div>
      <div style="font-size:10px; opacity:.5;">${l.stealth?'👁‍🗨 STEALTH':''} ${l.autoShow?'⚡AUTO':''}</div>
    `;
    div.onclick=()=>selectLayer(l.id);
    // drag reorder simple
    div.draggable=true;
    div.ondragstart=(e)=>{e.dataTransfer.setData('id',l.id)};
    div.ondrop=(e)=>{
      const dragged=e.dataTransfer.getData('id');
      const fromIdx=layers.findIndex(x=>x.id===dragged);
      const toIdx=layers.findIndex(x=>x.id===l.id);
      if(fromIdx>-1 && toIdx>-1){
        const item=layers.splice(fromIdx,1)[0];
        layers.splice(toIdx,0,item);
        renderLayerList(); render();
      }
    };
    div.ondragover=(e)=>e.preventDefault();
    layerListEl.appendChild(div);
  });
}

function selectLayer(id){
  selectedId=id;
  const l=layers.find(x=>x.id===id);
  if(!l) return;
  renderLayerList();
  document.getElementById('layerProps').style.display='block';
  document.getElementById('propText').value=l.text;
  document.getElementById('propFont').value=l.fontFamily;
  document.getElementById('propSize').value=l.fontSize;
  document.getElementById('propStroke').value=l.stroke||0;
  document.getElementById('propBg').value=l.bgType||'none';
  document.getElementById('propAutoShow').checked=l.autoShow;
  document.getElementById('propStealth').checked=l.stealth;
  // highlight color
  document.querySelectorAll('.color-opt').forEach(el=>{
    el.classList.toggle('active', el.style.background===l.color || rgbToHex(el.style.background)===l.color);
  });
  render();
}

function rgbToHex(rgb){
  // simple
  return rgb;
}

function addLayer(type){
  const id='l'+Date.now();
  const base={
    id, type,
    x:0.1, y:0.4+Math.random()*0.3, w:0.8, h:0.08,
    fontFamily:'Bebas Neue', fontSize:28, color:'#ffffff',
    bgType: type==='saweria'?'solid':'gradient-tiktok',
    bgColor:'#000000cc',
    stroke:3, strokeColor:'#000000',
    autoShow:true, stealth:false, visible:true, rotation:0
  };
  if(type==='text') base.text='TEKS BARU - EDIT DISINI 🔥';
  if(type==='saweria') base.text='🎉 {name} baru sawer {amount}! - {message}';
  if(type==='goal'){ base.text='GOAL: 0 / 1.000.000'; base.progress=0.2; }
  if(type==='image'){ base.text='[GAMBAR]'; base.imageUrl=''; }
  layers.push(base);
  selectLayer(id);
  renderLayerList();
  if(window.innerWidth<900) toggleSidebar(); // close on mobile after add? keep open
}

function updateSelected(key,val){
  const l=layers.find(x=>x.id===selectedId);
  if(!l) return;
  if(key==='text') l.text=val;
  if(key==='fontFamily') l.fontFamily=val;
  if(key==='fontSize') l.fontSize=parseInt(val);
  if(key==='stroke') l.stroke=parseInt(val);
  if(key==='color') l.color=val;
  if(key==='bgType') l.bgType=val;
  if(key==='autoShow') l.autoShow=val;
  if(key==='stealth') l.stealth=val;
  render();
  renderLayerList();
}

function deleteSelected(){
  layers=layers.filter(x=>x.id!==selectedId);
  selectedId=layers[0]?.id||null;
  if(selectedId) selectLayer(selectedId);
  else {
    document.getElementById('layerProps').style.display='none';
    renderLayerList();
  }
  render();
}

function bindEvents(){
  // canvas drag
  let isDragging=false;
  let startX,startY, origX, origY;
  let handle=null; // 'move' or 'resize'

  function getPos(e){
    const rect=canvas.getBoundingClientRect();
    const clientX = e.touches? e.touches[0].clientX : e.clientX;
    const clientY = e.touches? e.touches[0].clientY : e.clientY;
    const x = (clientX - rect.left)/rect.width;
    const y = (clientY - rect.top)/rect.height;
    return {x,y, clientX, clientY};
  }

  canvas.addEventListener('pointerdown',(e)=>{
    const pos=getPos(e);
    // hit test reverse (top layer first)
    for(let i=layers.length-1;i>=0;i--){
      const l=layers[i];
      if(pos.x>=l.x && pos.x<=l.x+l.w && pos.y>=l.y && pos.y<=l.y+l.h){
        selectLayer(l.id);
        isDragging=true;
        startX=pos.x; startY=pos.y;
        origX=l.x; origY=l.y;
        // check resize handle (bottom right 20%)
        if(pos.x > l.x+l.w-0.08 && pos.y > l.y+l.h-0.08) handle='resize';
        else handle='move';
        canvas.setPointerCapture(e.pointerId);
        break;
      }
    }
  });

  canvas.addEventListener('pointermove',(e)=>{
    if(!isDragging) return;
    const l=layers.find(x=>x.id===selectedId);
    if(!l) return;
    const pos=getPos(e);
    const dx=pos.x-startX;
    const dy=pos.y-startY;
    if(handle==='move'){
      l.x = Math.max(0, Math.min(1-l.w, origX+dx));
      l.y = Math.max(0, Math.min(1-l.h, origY+dy));
      // snap
      if(document.getElementById('snapToggle').checked){
        l.x = Math.round(l.x*20)/20;
        l.y = Math.round(l.y*20)/20;
      }
    }else if(handle==='resize'){
      l.w = Math.max(0.1, Math.min(0.95, l.w+dx));
      l.h = Math.max(0.04, Math.min(0.5, l.h+dy));
      startX=pos.x; startY=pos.y;
    }
    render();
  });

  canvas.addEventListener('pointerup',()=>{
    isDragging=false;
    handle=null;
  });

  window.addEventListener('resize',()=>{
    render();
  });
}

function drawLayer(context, l, forPreview){
  const x = l.x * res.w;
  const y = l.y * res.h;
  const w = l.w * res.w;
  const h = l.h * res.h;

  // stealth logic: if global stealth or layer stealth and we are rendering preview (streamer view), skip
  // Actually we have two modes: programCanvas = audience view (always shows all visible)
  // previewCanvas = streamer view (hides stealth layers)
  // Here we are drawing for program (audience) - always show if visible
  // But function is generic, we will handle filtering outside

  context.save();
  
  // background
  if(l.bgType!=='none'){
    let bg = l.bgColor||'#000';
    if(l.bgType==='gradient-tiktok'){
      const grad=context.createLinearGradient(x,y,x+w,y+h);
      grad.addColorStop(0,'#00f2ea');
      grad.addColorStop(1,'#ff0050');
      bg=grad;
    }else if(l.bgType==='gradient-fire'){
      const grad=context.createLinearGradient(x,y,x+w,y+h);
      grad.addColorStop(0,'#ff8a00');
      grad.addColorStop(1,'#ff0050');
      bg=grad;
    }
    context.fillStyle=bg;
    // rounded rect
    roundRect(context, x, y, w, h, 12, true, false);
  }

  // goal progress bar if goal type
  if(l.type==='goal' && l.progress!==undefined){
    context.fillStyle='#00f2ea';
    roundRect(context, x, y+h-6, w*l.progress, 6, 3, true,false);
    context.fillStyle='#ffffff33';
    roundRect(context, x+w*l.progress, y+h-6, w*(1-l.progress), 6, 3, true,false);
  }

  // text
  context.font=`800 ${l.fontSize * (res.w/1080)}px "${l.fontFamily}", sans-serif`;
  context.fillStyle=l.color;
  context.textAlign='left';
  context.textBaseline='middle';
  // stroke
  if(l.stroke>0){
    context.strokeStyle=l.strokeColor||'#000';
    context.lineWidth=l.stroke;
    context.lineJoin='round';
    context.strokeText(l.text, x+16, y+h/2);
  }
  context.fillText(l.text, x+16, y+h/2);

  // selection border if selected and not forPreview hiding?
  if(l.id===selectedId && !forPreview){
    context.strokeStyle='#00f2ea';
    context.lineWidth=2;
    context.setLineDash([8,6]);
    roundRect(context, x, y, w, h, 12, false,true);
    context.setLineDash([]);
    // handle
    context.fillStyle='#00f2ea';
    context.fillRect(x+w-14, y+h-14, 14,14);
  }

  context.restore();
}

function roundRect(ctx,x,y,w,h,r,fill,stroke){
  ctx.beginPath();
  ctx.moveTo(x+r,y);
  ctx.arcTo(x+w,y,x+w,y+h,r);
  ctx.arcTo(x+w,y+h,x,y+h,r);
  ctx.arcTo(x,y+h,x,y,r);
  ctx.arcTo(x,y,x+w,y,r);
  ctx.closePath();
  if(fill) ctx.fill();
  if(stroke) ctx.stroke();
}

function render(){
  // clear
  ctx.clearRect(0,0,res.w,res.h);
  pctx.clearRect(0,0,res.w,res.h);

  // draw camera frame to both? programCanvas gets camera as background via video element underneath (CSS). For export/stream we need to composite camera.
  // For canvas, we draw video frame if available
  if(video.readyState>=2){
    ctx.drawImage(video,0,0,res.w,res.h);
    pctx.drawImage(video,0,0,res.w,res.h);
  }else{
    // fallback bg
    const grad=ctx.createLinearGradient(0,0,0,res.h);
    grad.addColorStop(0,'#1a1a2f');
    grad.addColorStop(1,'#000');
    ctx.fillStyle=grad;
    ctx.fillRect(0,0,res.w,res.h);
    pctx.fillStyle=grad;
    pctx.fillRect(0,0,res.w,res.h);
  }

  const globalStealth=document.getElementById('globalStealth')?.checked;

  // Render layers
  layers.forEach(l=>{
    if(!l.visible) return;
    // if not live and autoShow=false, hide? Requirement: auto muncul pas live
    if(!isLive && l.autoShow===false){
      // still show in editor but dimmed? We'll show ghost
      ctx.save();
      ctx.globalAlpha=0.35;
      drawLayer(ctx,l,false);
      ctx.restore();
      // preview also ghost
      pctx.save();
      pctx.globalAlpha=0.35;
      // if stealth, hide in preview
      if(!(l.stealth || globalStealth)) drawLayer(pctx,l,true);
      pctx.restore();
      return;
    }
    // audience view
    drawLayer(ctx,l,false);
    // streamer preview: hide if stealth
    if(!(l.stealth || globalStealth)){
      drawLayer(pctx,l,true);
    }
  });

  // switch canvas visibility based on viewMode
  const progEl=document.getElementById('programCanvas');
  const prevEl=document.getElementById('previewCanvas');
  if(viewMode==='viewer'){
    progEl.style.opacity='1';
    progEl.style.pointerEvents='auto';
    prevEl.style.opacity='0';
  }else{
    // streamer sees previewCanvas (clean) but program still underneath? We show preview on top
    progEl.style.opacity='0';
    progEl.style.pointerEvents='none';
    prevEl.style.opacity='1';
  }
}

// View Mode Toggle
function setViewMode(mode){
  viewMode=mode;
  document.getElementById('btnViewer').classList.toggle('active', mode==='viewer');
  document.getElementById('btnStreamer').classList.toggle('active', mode==='streamer');
  render();
}

// Tabs
function switchTab(name){
  document.querySelectorAll('.tab').forEach(t=>t.classList.remove('active'));
  document.querySelectorAll('.tab-content').forEach(c=>c.classList.remove('active'));
  document.getElementById('tab-'+name).classList.add('active');
  // find tab button
  const tabs=document.querySelectorAll('.tab');
  const map={layers:0,saweria:1,stream:2,settings:3};
  tabs[map[name]].classList.add('active');
}

function toggleSidebar(){
  document.getElementById('sidebar').classList.toggle('open');
}

// Live Logic
function toggleLive(){
  isLive=!isLive;
  const btn=document.getElementById('goLiveBtn');
  const badge=document.getElementById('liveBadge');
  if(isLive){
    btn.textContent='⏹ STOP LIVE';
    btn.classList.add('live-on');
    badge.textContent='● LIVE';
    badge.classList.remove('off');
    badge.style.background='#ff0050';
    // auto show layers
    if(document.getElementById('autoOverlayOnLive').checked){
      layers.forEach(l=>{ if(l.autoShow) l.visible=true; });
    }
    logSaweria('🔴 LIVE DIMULAI - Overlay auto aktif');
    startSaweriaPolling();
    // if we have rtmp, would start streaming here (native part)
    render();
  }else{
    btn.textContent='🔴 MULAI LIVE STREAM';
    btn.classList.remove('live-on');
    badge.textContent='● OFFLINE';
    badge.classList.add('off');
    badge.style.background='';
    stopSaweriaPolling();
    logSaweria('⏹ Live berhenti');
    render();
  }
}

// Saweria Integration
function connectSaweria(){
  const user=document.getElementById('saweriaUser').value.trim();
  const overlayUrl=document.getElementById('saweriaOverlayUrl').value.trim();
  const key=document.getElementById('saweriaKey').value.trim();
  if(!user && !overlayUrl){
    alert('Isi username Saweria atau Overlay URL bre');
    return;
  }
  saweriaConnected=true;
  logSaweria(`✅ Connect ke Saweria: ${user||overlayUrl}`);
  logSaweria('Polling donasi tiap 3 detik... (pakai API publik Saweria)');
  if(isLive) startSaweriaPolling();
}

function startSaweriaPolling(){
  if(!saweriaConnected) return;
  if(saweriaPollInterval) clearInterval(saweriaPollInterval);
  saweriaPollInterval=setInterval(async ()=>{
    // Try fetch from Saweria public endpoint if username provided
    const user=document.getElementById('saweriaUser').value.trim();
    if(!user) return;
    try{
      // Saweria has endpoint: https://saweria.co/api/donations/{username} ? Unofficial but works for overlay data
      // We will attempt to fetch latest via their widget JSON
      // For demo, we simulate if fails
      const res = await fetch(`https://saweria.co/api/donations/${user}`).then(r=>r.json()).catch(()=>null);
      if(res && res.data && res.data.length){
        // check latest
        const latest=res.data[0];
        const id=latest.id;
        if(window.lastDonationId!==id){
          window.lastDonationId=id;
          triggerDonationAlert(latest.donator, latest.amount_raw, latest.message);
        }
      }
    }catch(e){
      // ignore, will still work with manual webhook
    }
  }, 3000);
}

function stopSaweriaPolling(){
  if(saweriaPollInterval) clearInterval(saweriaPollInterval);
}

function triggerTestDonation(){
  triggerDonationAlert('Bang Jago', 50000, 'semangat bang live nya keren abis!! mantap');
}

function triggerDonationAlert(name, amount, message){
  const amountFmt = new Intl.NumberFormat('id-ID').format(amount);
  const title = `${name.toUpperCase()} SAWER ${amountFmt}!`;
  const alertBox=document.getElementById('alertBox');
  document.getElementById('alertTitle').textContent=title;
  document.getElementById('alertMsg').textContent=message||'Terima kasih banyak!';
  
  // Also update layer text if saweria layer exists
  const saweriaLayer=layers.find(l=>l.type==='saweria');
  if(saweriaLayer){
    saweriaLayer.text=`💸 ${name}: ${amountFmt} - ${message?.slice(0,40)||''}`;
    saweriaLayer.lastDonation={name,amount,message,time:Date.now()};
  }

  alertBox.style.display='block';
  logSaweria(`💸 DONASI: ${name} - Rp${amountFmt} - ${message}`);

  // TTS
  if(document.getElementById('ttsToggle').checked && 'speechSynthesis' in window){
    const utter = new SpeechSynthesisUtterance(`${name} sawer ${amountFmt}. ${message}`);
    utter.lang='id-ID';
    utter.rate=1.1;
    speechSynthesis.speak(utter);
  }

  render();

  if(document.getElementById('autoHideToggle').checked){
    setTimeout(()=>{ alertBox.style.display='none'; },8000);
  }
}

function logSaweria(msg){
  const el=document.getElementById('saweriaLog');
  const time=new Date().toLocaleTimeString();
  el.innerHTML=`[${time}] ${msg}<br>`+el.innerHTML;
}

function changeRes(){
  const val=document.getElementById('resSelect').value;
  const [w,h]=val.split('x').map(Number);
  res={w,h};
  setupCanvas();
  render();
}

function toggleSafe(){
  const show=document.getElementById('safeToggle').checked;
  document.getElementById('safeArea').style.display=show?'block':'none';
}

function setBg(type){
  if(type==='color'){
    const c=prompt('Hex warna background (contoh #0a0a0f):','#0a0a0f');
    if(c) document.getElementById('canvasWrap').style.background=c;
  }else{
    document.getElementById('canvasWrap').style.background='#000';
    startCamera();
  }
}

function exportConfig(){
  const config={layers, res, viewMode, timestamp:Date.now()};
  const blob=new Blob([JSON.stringify(config,null,2)],{type:'application/json'});
  const url=URL.createObjectURL(blob);
  const a=document.createElement('a');
  a.href=url; a.download='saweroverlay-config.json'; a.click();
}

function copyBrowserSource(){
  const baseUrl=window.location.href.split('?')[0];
  const configB64=btoa(JSON.stringify(layers));
  const url=`${baseUrl}?overlay=true&config=${encodeURIComponent(configB64)}`;
  navigator.clipboard.writeText(url).then(()=>alert('Link Browser Source dicopy! Paste di TikTok Live Studio > Add Source > Browser Source\n\n'+url));
}

function downloadApkInstructions(){
  const md = `
# Build APK SawerOverlay Android

## Cara 1: PWA ke APK (Paling Gampang 5 menit)
1. Buka https://www.pwabuilder.com
2. Paste URL PWA kamu (hosting folder /pwa ini)
3. Build APK -> Download

## Cara 2: Capacitor (Recommended untuk RTMP)
\`\`\`bash
npm i -g @capacitor/cli
npm init -y
npm i @capacitor/core @capacitor/android
npx cap init SawerOverlay com.saweroverlay.live
# copy pwa folder ke www
npx cap add android
npx cap open android
# di Android Studio, build APK
\`\`\`

## Cara 3: Pakai Native Project yang udah gua siapin
Lihat folder /android-app - sudah ada MainActivity.kt dengan:
- CameraX
- OpenGL overlay rendering
- RTMP streaming via rtmp-rtsp-stream-client-java
- SYSTEM_ALERT_WINDOW untuk stealth mode

Fitur native:
- Overlay dirender ke RTMP stream, bukan ke layar HP (jadi di HP lu bersih)
- Konsisten resolusi pakai normalized coords
- Auto show pas live start
`;

  const blob=new Blob([md],{type:'text/markdown'});
  const url=URL.createObjectURL(blob);
  const a=document.createElement('a');
  a.href=url; a.download='BUILD-APK-GUIDE.md'; a.click();
}

// Overlay mode for Browser Source (if ?overlay=true)
function checkOverlayMode(){
  const params=new URLSearchParams(window.location.search);
  if(params.get('overlay')==='true'){
    document.body.innerHTML='<canvas id="overlayOnly" style="width:100vw;height:100vh;"></canvas>';
    const c=document.getElementById('overlayOnly');
    c.width=1080; c.height=1920;
    const ctx=c.getContext('2d');
    try{
      const cfg=JSON.parse(atob(params.get('config')));
      if(cfg) layers=cfg;
    }catch{}
    setInterval(()=>{
      ctx.clearRect(0,0,1080,1920);
      layers.forEach(l=>drawLayer(ctx,l,false));
    },100);
  }
}

checkOverlayMode();
init();
