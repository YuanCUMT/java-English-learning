document.getElementById('btnExample')?.addEventListener('click', async function () {
  if(this.disabled)return;
  const label=this.textContent;this.disabled=true;this.textContent='正在载入示例…';this.setAttribute('aria-busy','true');
  const hint=document.getElementById('exampleHint');hint.textContent='';
  try {
    const res=await fetch('/api/examples/load',{method:'POST'}); const body=await res.json();
    if(!res.ok || body.code!==0) throw Error(body.message || '示例暂时未能载入，请稍后再试');
    location.assign('/article/'+body.data.id);
  } catch(e) { hint.textContent=e.message || '网络连接失败，请检查连接后再试';this.disabled=false;this.textContent=label;this.removeAttribute('aria-busy'); }
});
