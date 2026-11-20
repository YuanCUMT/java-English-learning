/* Shared feedback and authentication form affordances. No automatic retry. */
(() => {
  'use strict';
  function notice(message, href, action) {
    let panel = document.getElementById('requestNotice');
    if (!panel) {
      panel = document.createElement('aside'); panel.id='requestNotice';
      panel.className='request-notice'; panel.setAttribute('role','alert');
      document.body.append(panel);
    }
    panel.replaceChildren();
    const text=document.createElement('p'); text.textContent=message;
    const actions=document.createElement('div'); actions.className='notice-actions';
    const link=document.createElement('a'); link.href=href; link.textContent=action;
    const close=document.createElement('button'); close.type='button'; close.textContent='暂时关闭';
    close.onclick=()=>{ panel.hidden=true; }; actions.append(link,close);panel.append(text,actions);panel.hidden=false;
  }
  window.__drNotice=notice;
  const original=window.fetch.bind(window);
  window.fetch=async (...args)=>{
    const response=await original(...args);
    const url=new URL(response.url || String(args[0]),location.href);
    if(url.origin===location.origin && url.pathname.startsWith('/api/') && [401,403].includes(response.status)) {
      const result=await response.clone().json().catch(()=>null);
      if(response.status===401) notice('登录状态已失效。重新登录后再继续，当前输入不会自动提交。','/login','重新登录');
      else if(result?.message?.includes('页面验证')) notice('页面验证已过期。请重新打开当前页后再操作；系统不会自动重复请求。',location.pathname,'重新打开页面');
    }
    return response;
  };
  function ready() {
    document.querySelectorAll('.auth input[type="password"]').forEach(input=>{
      const wrap=document.createElement('div');wrap.className='password-wrap';input.before(wrap);wrap.append(input);
      const toggle=document.createElement('button');toggle.type='button';toggle.className='password-toggle';toggle.textContent='显示';
      toggle.setAttribute('aria-label','显示'+(input.id==='confirm'?'确认密码':'密码'));toggle.setAttribute('aria-pressed','false');
      toggle.onclick=()=>{const show=input.type==='password';input.type=show?'text':'password';toggle.textContent=show?'隐藏':'显示';toggle.setAttribute('aria-pressed',String(show));toggle.setAttribute('aria-label',(show?'隐藏':'显示')+(input.id==='confirm'?'确认密码':'密码'));};
      wrap.append(toggle);
    });
    document.querySelectorAll('.auth form').forEach(form=>{
      form.addEventListener('submit',event=>{
        if(form.dataset.busy==='true'){event.preventDefault();return;}
        form.dataset.busy='true';const button=form.querySelector('button[type="submit"]');
        if(button){button.dataset.label=button.textContent;button.disabled=true;button.setAttribute('aria-busy','true');button.textContent='正在提交…';}
      });
    });
    document.querySelectorAll('.toast').forEach(x=>{x.setAttribute('role','status');x.setAttribute('aria-live','polite');});
    document.querySelectorAll('.article-loading').forEach(x=>{x.setAttribute('role','status');x.setAttribute('aria-live','polite');});
  }
  window.addEventListener('pageshow',()=>document.querySelectorAll('.auth form').forEach(form=>{
    delete form.dataset.busy;form.querySelectorAll('button[data-label]').forEach(b=>{b.disabled=false;b.removeAttribute('aria-busy');b.textContent=b.dataset.label;});
  }));
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',ready);else ready();
})();
