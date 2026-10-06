/**
 * OnlineJudge 前端主逻辑（PC 桌面端）
 * hash 路由 + 原生 DOM 渲染（无框架、无需构建）
 * 判题状态码对齐 oj-pojo 的 JudgeStatus 枚举；接口见 assets/js/api.js
 * 约定：后端返回的一切文本都用 textContent 渲染，绝不使用 innerHTML
 */
(function () {
  'use strict';

  /* ===================== 常量 ===================== */

  // status → [英文展示, 中文说明, 颜色]，对齐 JudgeStatus
  var STATUS = {
    0:  ['Pending', '排队中', 'gray'],
    1:  ['Judging', '正在判题', 'gray'],
    2:  ['Accepted', '通过', 'green'],
    3:  ['Partially Correct', '部分正确', 'orange'],
    4:  ['Wrong Answer', '答案错误', 'red'],
    5:  ['Time Limit Exceeded', '运行超时', 'red'],
    6:  ['Memory Limit Exceeded', '内存超限', 'red'],
    7:  ['Runtime Error', '运行时错误', 'red'],
    8:  ['Compile Error', '编译错误', 'red'],
    9:  ['Output Limit Exceeded', '输出超限', 'red'],
    10: ['Unknown Error', '未知错误', 'red'],
    11: ['Compile Success', '编译成功', 'gray']
  };
  var DIFF = { 1: '简单', 2: '中等', 3: '困难' };

  var PAGE_SIZE = 20;
  var POLL_INTERVAL = 800;   // 判题轮询间隔
  var POLL_MAX = 75;         // 最多 75 次 ≈ 60 秒

  var JAVA_TEMPLATE = [
    'import java.util.Scanner;',
    '',
    'public class Main {',
    '    public static void main(String[] args) {',
    '        Scanner sc = new Scanner(System.in);',
    '        // TODO: 在这里写你的代码',
    '',
    '        sc.close();',
    '    }',
    '}'
  ].join('\n');

  /* ===================== DOM 工具 ===================== */

  function $(id) { return document.getElementById(id); }

  // 创建元素：attrs 支持 class / text / value / onclick…；子节点支持字符串或元素
  function h(tag, attrs, kids) {
    var el = document.createElement(tag);
    attrs = attrs || {};
    Object.keys(attrs).forEach(function (k) {
      var v = attrs[k];
      if (v === null || v === undefined || v === false) return;
      if (k === 'class') el.className = v;
      else if (k === 'text') el.textContent = v;
      else if (k === 'value') el.value = v;
      else if (k.indexOf('on') === 0 && typeof v === 'function') el.addEventListener(k.slice(2), v);
      else if (v === true) el.setAttribute(k, '');
      else el.setAttribute(k, v);
    });
    (kids || []).forEach(function (c) {
      if (c === null || c === undefined || c === false) return;
      el.appendChild(typeof c === 'object' ? c : document.createTextNode(String(c)));
    });
    return el;
  }

  var SVG_NS = 'http://www.w3.org/2000/svg';
  function svg(paths, size) {
    var s = document.createElementNS(SVG_NS, 'svg');
    s.setAttribute('viewBox', '0 0 24 24');
    s.setAttribute('width', size || 20);
    s.setAttribute('height', size || 20);
    s.setAttribute('fill', 'none');
    s.setAttribute('stroke', 'currentColor');
    s.setAttribute('stroke-width', '1.5');
    s.setAttribute('stroke-linecap', 'round');
    s.setAttribute('stroke-linejoin', 'round');
    (Array.isArray(paths) ? paths : [paths]).forEach(function (d) {
      var p = document.createElementNS(SVG_NS, 'path');
      p.setAttribute('d', d);
      s.appendChild(p);
    });
    return s;
  }

  function clear(el) { while (el.firstChild) el.removeChild(el.firstChild); }

  // LocalDateTime 正常是 ISO 字符串；极端配置下 Jackson 会序列化成数组，两种都兜住
  function fmtTime(t) {
    if (!t) return '';
    if (Array.isArray(t)) {
      var p = t.map(function (n) { return String(n).length < 2 ? '0' + n : String(n); });
      return p[0] + '-' + p[1] + '-' + p[2] + ' ' + p[3] + ':' + p[4] + ':' + p[5];
    }
    return String(t).replace('T', ' ').slice(0, 19);
  }

  function parseDate(t) {
    if (!t) return null;
    if (Array.isArray(t)) return new Date(t[0], (t[1] || 1) - 1, t[2] || 1, t[3] || 0, t[4] || 0, t[5] || 0);
    var d = new Date(String(t).replace(' ', 'T'));
    return isNaN(d.getTime()) ? null : d;
  }

  function relTime(t) {
    var d = parseDate(t);
    if (!d) return '';
    var diff = Date.now() - d.getTime();
    if (diff < 60000) return '刚刚';
    if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前';
    if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前';
    if (diff < 2592000000) return Math.floor(diff / 86400000) + ' 天前';
    return fmtTime(t).slice(0, 10);
  }

  function rate(submit, pass) {
    if (!submit) return '—';
    return (pass / submit * 100).toFixed(1) + '%';
  }

  function statusOf(code) { return STATUS[code] || ['Unknown', '未知状态 ' + code, 'gray']; }
  function badgeCls(color) {
    if (color === 'green') return 'ac';
    if (color === 'orange') return 'pc';
    if (color === 'red') return 'fail';
    return 'run';
  }
  function isFinal(s) { return !!s && s.status >= 2 && s.status !== 11; }
  function codeKey(pid) { return 'oj_code_' + pid; }

  function initialOf(name) {
    var s = String(name || 'U').trim();
    return (s.charAt(0) || 'U').toUpperCase();
  }

  function safeUrl(u) {
    var s = String(u || '').trim();
    if (!s) return '';
    return /^https?:\/\//i.test(s) ? s : 'https://' + s;
  }

  /* ===================== 主题：日间 / 夜间手动切换 ===================== */
  // 存 localStorage 的 oj_theme（light / dark）；没存过就跟随系统 prefers-color-scheme
  var THEME_KEY = 'oj_theme';

  function storedTheme() {
    try { return localStorage.getItem(THEME_KEY) || ''; } catch (e) { return ''; }
  }
  function systemDark() {
    return !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
  }
  function currentTheme() {
    var t = storedTheme();
    if (t === 'dark' || t === 'light') return t;
    return systemDark() ? 'dark' : 'light';
  }
  function applyTheme(t) {
    // 没存过就不加 data-theme，交回给系统的 prefers-color-scheme
    if (t === 'light' || t === 'dark') document.documentElement.setAttribute('data-theme', t);
    else document.documentElement.removeAttribute('data-theme');
    updateThemeButtons();
  }
  function toggleTheme() {
    var next = currentTheme() === 'dark' ? 'light' : 'dark';
    try { localStorage.setItem(THEME_KEY, next); } catch (e) { /* 忽略 */ }
    applyTheme(next);
  }

  // 当前是深色就显示太阳（点了回日间），当前是浅色就显示月亮（点了去夜间）
  function themeIcon(dark) {
    return dark
      ? svg(['M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0', 'M12 3v2', 'M12 19v2', 'M3 12h2', 'M19 12h2',
             'M5.6 5.6 4.2 4.2', 'M19.8 19.8l-1.4-1.4', 'M5.6 18.4l-1.4 1.4', 'M19.8 4.2l-1.4 1.4'], 15)
      : svg(['M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8'], 15);
  }

  function updateThemeButtons() {
    var dark = currentTheme() === 'dark';
    Array.prototype.forEach.call(document.querySelectorAll('.theme-toggle'), function (btn) {
      clear(btn);
      btn.appendChild(themeIcon(dark));
      btn.title = dark ? '切换到日间模式' : '切换到夜间模式';
    });
  }

  function themeToggle() {
    var btn = h('button', { class: 'theme-toggle', onclick: toggleTheme });
    btn.appendChild(themeIcon(currentTheme() === 'dark'));
    btn.title = currentTheme() === 'dark' ? '切换到日间模式' : '切换到夜间模式';
    return btn;
  }

  /* ===================== Java 代码高亮（自写轻量 tokenizer） ===================== */
  // 逐段扫描生成 <span>，全程 textContent，不拼 HTML 字符串
  var JAVA_KEYWORD = /^(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|true|false|null|var|record|sealed|yield)$/;

  function highlightJava(src) {
    var frag = document.createDocumentFragment();
    var rest = String(src || '');
    while (rest.length) {
      var ws = /^\s+/.exec(rest);
      if (ws) {
        frag.appendChild(document.createTextNode(ws[0]));
        rest = rest.slice(ws[0].length);
        continue;
      }
      var text = null, cls = '', m;
      if ((m = /^\/\/[^\n]*|^\/\*[\s\S]*?\*\//.exec(rest))) { text = m[0]; cls = 'tok-comment'; }
      else if ((m = /^"(?:\\.|[^"\\\n])*"?/.exec(rest))) { text = m[0]; cls = 'tok-string'; }
      else if ((m = /^'(?:\\.|[^'\\\n])*'?/.exec(rest))) { text = m[0]; cls = 'tok-string'; }
      else if ((m = /^@[A-Za-z_$][\w$]*/.exec(rest))) { text = m[0]; cls = 'tok-annotation'; }
      else if ((m = /^\d[\w.]*/.exec(rest))) { text = m[0]; cls = 'tok-number'; }
      else if ((m = /^[A-Za-z_$][\w$]*/.exec(rest))) {
        text = m[0];
        if (JAVA_KEYWORD.test(text)) cls = 'tok-keyword';
        else if (/^[A-Z]/.test(text)) cls = 'tok-type';         // 大写开头当类名
        else if (/^\s*\(/.test(rest.slice(text.length))) cls = 'tok-func'; // 后面跟括号当方法
      } else if ((m = /^[^\w\s]+/.exec(rest))) { text = m[0]; cls = 'tok-punct'; }
      else { text = rest.charAt(0); }

      if (cls) {
        var span = document.createElement('span');
        span.className = cls;
        span.textContent = text;
        frag.appendChild(span);
      } else {
        // 没有样式的 token（普通标识符等）不必包 span —— 每次重绘能省下一批节点
        frag.appendChild(document.createTextNode(text));
      }
      rest = rest.slice(text.length);
    }
    return frag;
  }

  /* ===================== 基础组件 ===================== */

  var toastTimer = null;
  function toast(msg, type) {
    var t = $('toast');
    if (!t) return;
    t.textContent = msg;
    t.className = 'toast' + (type ? ' ' + type : '');
    t.hidden = false;
    requestAnimationFrame(function () { t.classList.add('show'); });
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      t.classList.remove('show');
      setTimeout(function () { t.hidden = true; }, 220);
    }, 2200);
  }

  function spinnerRow(text) {
    return h('div', { class: 'spinner-row' }, [h('span', { class: 'spinner' }), h('span', { text: text || '加载中…' })]);
  }

  function emptyState(title, text, extra) {
    var ic = svg(['M4 7h16', 'M4 12h16', 'M4 17h10'], 30);
    ic.setAttribute('class', 'state-icon');
    var kids = [ic, h('div', { class: 'state-title', text: title }), h('div', { class: 'state-text', text: text || '' })];
    if (extra) kids.push(extra);
    return h('div', { class: 'state' }, kids);
  }

  // 表格：headers 为字符串或 [文本, class]
  function tableEl(headers, rows) {
    var tr = h('tr');
    headers.forEach(function (hd) {
      tr.appendChild(h('th', { class: Array.isArray(hd) ? hd[1] : null, text: Array.isArray(hd) ? hd[0] : hd }));
    });
    return h('div', { class: 'table-wrap' }, [
      h('table', { class: 'table' }, [h('thead', {}, [tr]), h('tbody', {}, rows)])
    ]);
  }

  // 竖排表单单元（PC 表单）
  function field(label, value, placeholder, type) {
    var inp = h('input', {
      class: 'input', type: type || 'text',
      placeholder: placeholder || '',
      value: value === null || value === undefined ? '' : String(value)
    });
    return { wrap: h('div', { class: 'field' }, [h('label', { text: label }), inp]), input: inp };
  }

  function genderField(value) {
    var sel = h('select', { class: 'input' }, [
      h('option', { value: '', text: '未填写' }),
      h('option', { value: '男', text: '男' }),
      h('option', { value: '女', text: '女' })
    ]);
    sel.value = value || '';
    return { wrap: h('div', { class: 'field' }, [h('label', { text: '性别' }), sel]), input: sel };
  }

  // 带候选下拉的搜索框（提交记录页的筛选）
  //   search(kw) → Promise<[{ id, label, hint }]>；选中某项后回调 onPick(item)
  //   onClear：输入框被清空时回调，用来撤掉这一条筛选
  // 返回 { el, input }：el 放进工具栏，input 用来回填已选中的名字
  function searchPicker(placeholder, search, onPick, onClear) {
    var input = h('input', { class: 'input', type: 'search', placeholder: placeholder, autocomplete: 'off' });
    var list = h('div', { class: 'picker-list' });
    list.hidden = true;
    var box = h('div', { class: 'picker' }, [input, list]);
    var timer = null;

    function close() { list.hidden = true; clear(list); }

    function show(items) {
      clear(list);
      if (!items.length) { list.hidden = true; return; }
      items.forEach(function (it) {
        var row = h('div', { class: 'picker-item' }, [
          h('span', { class: 'picker-label', text: it.label }),
          it.hint ? h('span', { class: 'picker-hint', text: it.hint }) : null
        ]);
        // 用 mousedown 而不是 click：click 之前 blur 会先把下拉收掉，行就点不到了
        row.addEventListener('mousedown', function (e) {
          e.preventDefault();
          input.value = it.label;
          close();
          onPick(it);
        });
        list.appendChild(row);
      });
      list.hidden = false;
    }

    input.addEventListener('input', function () {
      var kw = input.value.trim();
      clearTimeout(timer);
      if (!kw) { close(); if (onClear) onClear(); return; }
      timer = setTimeout(function () {
        Promise.resolve(search(kw)).then(function (items) {
          if (input.value.trim() !== kw) return;   // 结果回来时关键词已经变了，丢弃
          show(items);
        }).catch(close);
      }, 250);
    });
    // 失焦后稍等一下再收，给 mousedown 留出触发时间
    input.addEventListener('blur', function () { setTimeout(close, 150); });
    input.addEventListener('keydown', function (e) { if (e.key === 'Escape') { close(); input.blur(); } });

    return { el: box, input: input };
  }

  function kvRow(k, v) {
    return h('div', { class: 'kv' }, [h('span', { class: 'k', text: k }), h('span', { class: 'v', text: v || '未填写' })]);
  }

  function diffBadge(d) {
    return h('span', { class: 'badge d' + (DIFF[d] ? d : 0), text: DIFF[d] || '未知' });
  }

  function statusBadge(code) {
    var m = statusOf(code);
    return h('span', { class: 'badge ' + badgeCls(m[2]), text: m[1] });
  }

  function avatar(url, name, big) {
    var cls = big ? 'avatar-lg' : 'avatar-sm';
    if (url) {
      var img = h('img', { class: cls, src: url, alt: 'avatar' });
      img.addEventListener('error', function () {
        var fb = h('div', { class: cls, text: initialOf(name) });
        if (img.parentNode) img.parentNode.replaceChild(fb, img);
      });
      return img;
    }
    return h('div', { class: cls, text: initialOf(name) });
  }

  // 分页条：总条数 + 上一页 / 页码下拉 / 下一页 + 每页条数
  // pageNo 以接口返回的为准（它是这次请求真正用的页号），不用 state 里可能已经变旧的值
  var PAGE_SIZES = [10, 20, 50, 100];

  function pagerBar(page, state, reload) {
    var total = (page && page.total) || 0;
    var pageNo = (page && (page.pageNo || page.pageNO)) || state.pageNO;
    var pages = Math.max(1, Math.ceil(total / state.pageSize));
    if (pageNo > pages) pageNo = pages;

    function go(n) {
      if (n < 1 || n > pages || n === pageNo) return;
      state.pageNO = n;
      reload();
    }

    var prev = h('button', { class: 'btn btn-sm', text: '上一页', onclick: function () { go(pageNo - 1); } });
    prev.disabled = pageNo <= 1;
    var next = h('button', { class: 'btn btn-sm', text: '下一页', onclick: function () { go(pageNo + 1); } });
    next.disabled = pageNo >= pages;

    // 页码下拉：页数多时也能直接跳，不用一页页点
    var pageSel = h('select', { class: 'input page-no', title: '选择页码' });
    for (var i = 1; i <= pages; i++) pageSel.appendChild(h('option', { value: String(i), text: String(i) }));
    pageSel.value = String(pageNo);
    pageSel.addEventListener('change', function () { go(parseInt(pageSel.value, 10)); });

    var sizeSel = h('select', { class: 'input page-size', title: '每页条数' });
    PAGE_SIZES.forEach(function (n) {
      sizeSel.appendChild(h('option', { value: String(n), text: n + ' 条/页' }));
    });
    // 状态里存的每页条数不在预设列表里（比如从别处带进来的），补一个选项免得选中项对不上
    if (PAGE_SIZES.indexOf(state.pageSize) < 0) {
      sizeSel.appendChild(h('option', { value: String(state.pageSize), text: state.pageSize + ' 条/页' }));
    }
    sizeSel.value = String(state.pageSize);
    sizeSel.addEventListener('change', function () {
      state.pageSize = parseInt(sizeSel.value, 10);
      state.pageNO = 1;       // 每页条数变了，原来的页号没意义，回第一页
      reload();
    });

    return h('div', { class: 'pager' }, [
      h('span', { class: 'info', text: '共 ' + total + ' 条' }),
      h('div', { class: 'pager-right' }, [
        prev, pageSel, h('span', { class: 'of', text: '/ ' + pages + ' 页' }), next, sizeSel
      ])
    ]);
  }

  function pageHead(title, sub) {
    return h('div', { class: 'page-head' }, [
      h('h1', { class: 'page-title', text: title }),
      sub ? h('p', { class: 'page-sub', text: sub }) : null
    ]);
  }

  /* ===================== 顶部导航 ===================== */

  function renderNav(activeKey) {
    var map = { problems: 'problems', problem: 'problems', submit: 'submits', submits: 'submits', rank: 'rank' };
    var cur = map[activeKey] || '';
    Array.prototype.forEach.call(document.querySelectorAll('#navLinks a'), function (a) {
      a.classList.toggle('active', a.getAttribute('data-nav') === cur);
    });

    var box = $('navRight');
    clear(box);
    if (api.isLogin()) {
      var user = api.getUser() || {};
      box.appendChild(h('div', { class: 'nav-user' }, [
        avatar(user.avatar, user.username, false),
        h('a', { class: 'username', href: '#/profile', text: user.username || '我的' })
      ]));
      box.appendChild(h('button', { class: 'btn btn-sm', text: '退出', onclick: doLogout }));
    } else {
      box.appendChild(h('button', { class: 'btn btn-sm', text: '登录', onclick: function () { showLoginModal(false); } }));
      box.appendChild(h('button', { class: 'btn btn-sm btn-primary', text: '注册', onclick: function () { showLoginModal(true); } }));
    }
    box.appendChild(themeToggle());
  }

  /* ===================== 模态弹窗 ===================== */

  var modals = [];

  function openModal(title, nodes, wide) {
    var body = h('div', { class: 'modal-body' }, nodes);
    var closeBtn = h('button', { class: 'modal-close', text: '×', title: '关闭' });
    var dialog = h('div', { class: 'modal' + (wide ? ' wide' : '') }, [
      h('div', { class: 'modal-head' }, [h('h3', { class: 'modal-title', text: title }), closeBtn]),
      body
    ]);
    var mask = h('div', { class: 'modal-mask' }, [dialog]);
    mask.addEventListener('click', function (e) { if (e.target === mask) close(); });
    document.body.appendChild(mask);
    requestAnimationFrame(function () { mask.classList.add('show'); });

    var obj = { body: body, closed: false, close: close };
    modals.push(obj);
    closeBtn.addEventListener('click', close);

    function close() {
      if (obj.closed) return;
      obj.closed = true;
      var i = modals.indexOf(obj);
      if (i >= 0) modals.splice(i, 1);
      mask.classList.remove('show');
      setTimeout(function () { if (mask.parentNode) mask.parentNode.removeChild(mask); }, 200);
    }
    return obj;
  }

  function closeAllModals() { modals.slice().forEach(function (m) { m.close(); }); }

  /* ===================== 登录 / 注册 ===================== */

  var pendingSubmit = null;   // 未登录时点了提交，登录成功后自动补上

  function showLoginModal(startRegister) {
    var err = h('p', { class: 'modal-error' });
    var uF = field('用户名', '', '字母 / 数字 / 下划线，至少 3 位');
    var eF = field('邮箱', '', 'you@example.com');
    var pF = field('密码', '', '至少 6 位，不含空格', 'password');
    var submit = h('button', { class: 'btn btn-primary btn-block', text: '登录' });
    var switchBox = h('div', { class: 'modal-switch' });
    var isReg = !!startRegister;

    var modal = openModal('登录', [
      uF.wrap, eF.wrap, pF.wrap,
      h('div', { class: 'modal-foot' }, [submit]),
      err,
      h('p', { class: 'modal-tip', text: '浏览题库和题面无需登录；提交代码、查看排行榜与个人中心需要登录。' }),
      switchBox
    ]);

    function sync() {
      eF.wrap.hidden = !isReg;
      submit.textContent = isReg ? '注册并登录' : '登录';
      clear(switchBox);
      switchBox.appendChild(h('span', { text: isReg ? '已有账号？' : '还没有账号？' }));
      var a = h('button', { class: 'btn-link', text: isReg ? '去登录' : '立即注册' });
      a.addEventListener('click', function () { isReg = !isReg; err.textContent = ''; sync(); });
      switchBox.appendChild(a);
    }
    sync();

    function fire() {
      var u = uF.input.value.trim();
      var pw = pF.input.value;
      var em = eF.input.value.trim();
      err.textContent = '';
      if (!u || !pw || (isReg && !em)) { err.textContent = '请完整填写表单'; return; }
      submit.disabled = true;
      var task = isReg
        ? api.register({ username: u, email: em, password: pw }).then(function () { return api.login({ username: u, password: pw }); })
        : api.login({ username: u, password: pw });

      task.then(function (data) {
        api.setToken(data.token);
        api.setUser({ id: data.id, username: data.username, avatar: data.avatar || '' });
        acceptSet = null;        // 换了个人，已通过的题目要重新拉
        modal.close();
        toast('登录成功', 'ok');
        router();
        // 登录前点过提交的话，登录成功后自动继续
        if (pendingSubmit !== null) {
          var pid = pendingSubmit;
          pendingSubmit = null;
          setTimeout(function () { doSubmit(pid); }, 400);
        }
      }).catch(function (e) {
        err.textContent = e.message;
      }).then(function () {
        submit.disabled = false;
      });
    }

    submit.addEventListener('click', fire);
    [uF.input, eF.input, pF.input].forEach(function (inp) {
      inp.addEventListener('keydown', function (e) { if (e.key === 'Enter') fire(); });
    });
    return modal;
  }

  function doLogout() {
    api.logout().catch(function () { /* 后端 logout 无实际逻辑，失败也清本地 */ });
    api.clearAuth();
    acceptSet = null;          // 退出后不该再看到「已通过」
    toast('已退出登录');
    location.hash = '#/problems';
  }

  /* ===================== 判题结果 ===================== */

  // 判题结果卡片：题目页右侧的内联小面板（不传 opts.big）和提交详情页（opts.big）共用
  function resultCard(s, opts) {
    opts = opts || {};
    var meta = statusOf(s.status);
    var color = badgeCls(meta[2]);

    var top = h('div', { class: 'result-top' }, [
      h('span', { class: 'badge ' + color + (opts.big ? ' badge-lg' : ''), text: meta[1] }),
      h('span', { class: opts.big ? 'result-en' : 'result-cn', text: meta[0] })
    ]);
    if (!opts.big) top.appendChild(h('span', { class: 'result-cn', text: '· 提交 #' + s.id }));

    var card = h('div', { class: 'result-card ' + color + (opts.big ? ' big' : '') }, [top]);

    // 元信息：详情页信息多，用网格排开看得清；题目页的内联面板压成一行
    var items = [];
    if (opts.title) items.push(['题目', opts.title]);
    if (s.username) items.push(['提交者', s.username]);
    if (s.timeUsed !== null && s.timeUsed !== undefined) items.push(['耗时', s.timeUsed + ' ms']);
    if (s.memoryUsed !== null && s.memoryUsed !== undefined) items.push(['内存', s.memoryUsed + ' KB']);
    if (s.submitLanguage) items.push(['语言', s.submitLanguage]);
    if (s.submitTime) items.push(['提交时间', fmtTime(s.submitTime)]);

    if (opts.big) {
      var grid = h('div', { class: 'result-grid' });
      items.forEach(function (it) {
        grid.appendChild(h('div', {}, [h('div', { class: 'k', text: it[0] }), h('div', { class: 'v', text: String(it[1]) })]));
      });
      if (items.length) card.appendChild(grid);
    } else {
      var line = h('div', { class: 'result-meta' });
      items.forEach(function (it) { line.appendChild(h('span', { text: it[0] + ' ' + it[1] })); });
      card.appendChild(line);
    }

    if (s.failedCaseNo) card.appendChild(h('div', { class: 'result-note', text: '第 ' + s.failedCaseNo + ' 个测试点未通过' }));
    if (s.errorMsg) card.appendChild(h('pre', { class: 'result-err', text: s.errorMsg }));

    var actions = opts.actions || defaultResultActions(s);
    if (actions.length) card.appendChild(h('div', { class: 'result-actions' }, actions));
    return card;
  }

  function defaultResultActions(s) {
    var list = [];
    if (s.problemId) {
      list.push(h('button', {
        class: 'btn-link', text: '查看本题提交记录',
        onclick: function () { location.hash = '#/submits?problemId=' + s.problemId; }
      }));
    }
    if (s.id) {
      list.push(h('button', {
        class: 'btn-link', text: '查看本次提交详情',
        onclick: function () { location.hash = '#/submit/' + s.id; }
      }));
    }
    return list;
  }

  function drawResult(box, s) {
    if (s.status === 2) markAccepted(s.problemId);   // AC 了，题库里的标记立刻跟上
    box.appendChild(resultCard(s));
  }

  // 只读代码展示（语法高亮 + 复制）
  function codeCard(code, lang) {
    var body = h('code');
    body.appendChild(highlightJava(code));
    var copyBtn = h('button', { class: 'btn-link', text: '复制代码' });
    copyBtn.addEventListener('click', function () { copyText(code, copyBtn); });
    return h('div', { class: 'card' }, [
      h('div', { class: 'card-head' }, [
        h('span', { text: '提交的代码' }),
        h('span', { class: 'hint', text: lang || 'Java' })
      ]),
      h('pre', { class: 'code-view-body' }, [body]),
      h('div', { class: 'card-foot' }, [
        copyBtn,
        h('span', { class: 'cell-muted', style: 'margin-left:auto', text: '共 ' + String(code).split('\n').length + ' 行' })
      ])
    ]);
  }

  function drawTimeout(box, s) {
    box.appendChild(h('div', { class: 'result-card' }, [
      h('div', { class: 'result-top' }, [h('span', { class: 'badge run', text: '判题超时' })]),
      h('div', { class: 'result-meta' }, [h('span', { text: '仍在排队或判题机繁忙，请稍后在提交记录中查看' })]),
      s && s.problemId
        ? h('div', { class: 'result-actions' }, [h('button', { class: 'btn-link', text: '查看提交记录', onclick: function () { location.hash = '#/submits?problemId=' + s.problemId; } })])
        : null
    ]));
  }

  // 轮询到终态（cancelled 用于在弹窗关闭后停止）
  function pollInto(box, id, n, cancelled) {
    n = n || 0;
    setTimeout(function () {
      if (cancelled && cancelled()) return;
      api.submitDetail(id).then(function (s) {
        if (cancelled && cancelled()) return;
        if (!isFinal(s) && n + 1 < POLL_MAX) { pollInto(box, id, n + 1, cancelled); return; }
        clear(box);
        if (isFinal(s)) drawResult(box, s); else drawTimeout(box, s);
      }).catch(function (e) {
        if (cancelled && cancelled()) return;
        clear(box);
        box.appendChild(h('div', { class: 'state error' }, [
          h('div', { class: 'state-title', text: '查询判题结果失败' }),
          h('div', { class: 'state-text', text: e.message })
        ]));
      });
    }, POLL_INTERVAL);
  }

  /* ===================== 页面：提交详情 #/submit/:id ===================== */

  // 列表里有题目名，详情接口（SubmitDetailVO）不带，进详情前先缓存一下
  var submitTitleCache = {};

  function renderSubmitDetail(ctx) {
    var app = ctx.app;
    var id = parseInt(ctx.seg[1], 10);
    if (!id) return Promise.reject(new Error('提交 id 非法'));

    // 离开本页就停止轮询
    function stillHere() {
      var r = parseHash();
      return r.seg[0] === 'submit' && parseInt(r.seg[1], 10) === id;
    }

    function draw(s) {
      clear(app);
      // 题目名优先取列表页缓存，其次取接口返回（后端目前不返回），最后退回题目 id
      var title = submitTitleCache[id] || s.title || '';
      var sub = title || (s.problemId ? ('题目 #' + s.problemId) : '');

      app.appendChild(h('a', { class: 'back-link', href: '#/submits', text: '← 返回提交记录' }));
      app.appendChild(pageHead('提交 #' + s.id, sub));

      // 详情页本身就在讲这一次提交，再放「查看本题提交记录」是绕路；要跳题面直接点「查看题目」
      var actions = [];
      if (s.problemId) {
        actions.push(h('a', { class: 'btn', href: '#/problem/' + s.problemId, text: '查看题目' }));
      }
      // 不再套一层 .card：result-card 自己就有边框，套起来是「框里套框」
      app.appendChild(resultCard(s, { big: true, title: title, actions: actions }));

      // SubmitDetailVO 现在带 code：有就展示源码，没有给一句说明
      if (s.code) {
        app.appendChild(codeCard(s.code, s.submitLanguage));
      } else {
        app.appendChild(h('div', { class: 'card' }, [
          h('div', { class: 'card-body' }, [
            h('div', { class: 'cell-muted', text: '本次提交没有返回源代码' })
          ])
        ]));
      }

      if (!isFinal(s)) {
        app.appendChild(h('div', { class: 'card' }, [
          h('div', { class: 'card-body' }, [spinnerRow('正在判题，结果出来后会自动刷新…')])
        ]));
      }
    }

    // 还没判完就继续轮询
    function poll(n) {
      setTimeout(function () {
        if (!stillHere()) return;
        api.submitDetail(id).then(function (s) {
          if (!stillHere()) return;
          draw(s);
          if (!isFinal(s) && n + 1 < POLL_MAX) poll(n + 1);
        }).catch(function () {
          if (!stillHere()) return;
          if (n + 1 < POLL_MAX) poll(n + 1);   // 单次失败不打断，继续等
        });
      }, POLL_INTERVAL);
    }

    return api.submitDetail(id).then(function (s) {
      draw(s);
      if (!isFinal(s)) poll(0);
    });
  }

  /* ===================== 页面：题库列表 ===================== */

  var problemState = { pageNO: 1, pageSize: PAGE_SIZE, difficulty: '', title: '' };

  // 当前用户 AC 过的题目 id（拿 id 当 key）。null = 还没拉过；未登录时是空对象
  var acceptSet = null;

  // GET /user/accept/problem 一次拿全部 AC 题目，各页面共用，避免每页都请求一遍。
  // 登录状态变化时置 null 失效，见 showLoginModal / doLogout
  function loadAcceptSet() {
    if (!api.isLogin()) { acceptSet = {}; return Promise.resolve(acceptSet); }
    if (acceptSet) return Promise.resolve(acceptSet);
    return api.myAcceptProblems().then(function (ids) {
      var set = {};
      (ids || []).forEach(function (id) { set[id] = true; });
      acceptSet = set;
      return set;
    }).catch(function () {
      acceptSet = {};      // 拉不到就当没做过，别把题库整个卡在加载中
      return acceptSet;
    });
  }

  function isAccepted(id) { return !!(acceptSet && acceptSet[id]); }

  // 判出 AC 就地补一条，这样刚过完题回题库列表不用再请求一次
  function markAccepted(id) { if (acceptSet && id) acceptSet[id] = true; }

  function renderProblems(ctx) {
    var app = ctx.app;

    function load() {
      var params = { pageNO: problemState.pageNO, pageSize: problemState.pageSize };
      if (problemState.difficulty) params.difficulty = problemState.difficulty;
      if (problemState.title) params.title = problemState.title;
      return loadAcceptSet().then(function () { return api.problemPage(params); }).then(draw);
    }

    function draw(page) {
      clear(app);
      var total = (page && page.total) || 0;
      app.appendChild(pageHead('题库', '共 ' + total + ' 道题，点击题目进入作答'));

      var card = h('div', { class: 'card' });

      // 工具条：关键词 + 难度（后端 difficulty 传 1/2/3）
      // 选项文字直接取 DIFF，免得改了难度名这里忘了跟着改
      var kw = h('input', { class: 'input search-input', type: 'search', placeholder: '搜索题目标题，回车查询', value: problemState.title });
      var sel = h('select', { class: 'input' }, [h('option', { value: '', text: '全部难度' })]);
      Object.keys(DIFF).forEach(function (d) {
        sel.appendChild(h('option', { value: d, text: DIFF[d] }));
      });
      sel.value = problemState.difficulty || '';
      var btn = h('button', { class: 'btn btn-primary', text: '搜索', onclick: function () {
        problemState.title = kw.value.trim();
        problemState.difficulty = sel.value;
        problemState.pageNO = 1;
        load();
      } });
      kw.addEventListener('keydown', function (e) { if (e.key === 'Enter') btn.click(); });
      sel.addEventListener('change', function () { problemState.difficulty = sel.value; problemState.pageNO = 1; load(); });
      card.appendChild(h('div', { class: 'toolbar' }, [kw, sel, btn]));

      var rows = (page && page.data) || [];
      if (!rows.length) {
        card.appendChild(emptyState('没有匹配的题目', '换个关键词或难度试试'));
        app.appendChild(card);
        return;
      }

      var trs = rows.map(function (p) {
        return h('tr', { class: 'row-link', onclick: function () { location.hash = '#/problem/' + p.id; } }, [
          h('td', { class: 'col-id mono', text: String(p.id) }),
          h('td', {}, [
            h('a', { href: '#/problem/' + p.id, text: p.title }),
            isAccepted(p.id) ? h('span', { class: 'badge ac mark-ac', text: '已通过' }) : null
          ]),
          h('td', { class: 'col-diff' }, [diffBadge(p.difficulty)]),
          h('td', { class: 'num', text: rate(p.submitTotal, p.passTotal) }),
          h('td', { class: 'num', text: (p.submitTotal || 0) + ' / ' + (p.passTotal || 0) })
        ]);
      });
      card.appendChild(tableEl([['#', 'col-id'], '题目', '难度', ['通过率', 'num'], ['提交 / 通过', 'num']], trs));
      card.appendChild(pagerBar(page, problemState, load));
      app.appendChild(card);
    }

    return load();
  }

  /* ===================== 页面：题目详情（左题面 / 右编辑器） ===================== */

  var editorCtx = null;

  function renderProblemView(ctx) {
    var app = ctx.app;
    var id = parseInt(ctx.seg[1], 10);
    if (!id) return Promise.reject(new Error('题目 id 非法'));

    return loadAcceptSet().then(function () { return api.problemDetail(id); }).then(function (p) {
      clear(app);

      /* ---- 左栏：题面 ---- */
      var left = h('div', {}, [
        h('a', { class: 'back-link', href: '#/problems', text: '← 返回题库' })
      ]);

      var body = h('div', { class: 'card-body' }, [
        h('h1', { class: 'problem-title', text: (p.id != null ? p.id + '. ' : '') + (p.title || '') }),
        h('div', { class: 'meta-row' }, [
          diffBadge(p.difficulty),
          isAccepted(p.id) ? h('span', { class: 'badge ac', text: '已通过' }) : null,
          h('span', { text: '时间限制 ' + (p.timeLimit != null ? p.timeLimit : '-') + ' ms' }),
          h('span', { text: '内存限制 ' + (p.memoryLimit != null ? p.memoryLimit : '-') + ' MB' }),
          h('span', { text: '通过率 ' + rate(p.submitTotal, p.passTotal) }),
          h('span', { text: '提交 ' + (p.submitTotal || 0) + ' · 通过 ' + (p.passTotal || 0) }),
          p.author ? h('span', { text: '作者 ' + p.author }) : null
        ])
      ]);

      if (p.problemTags && p.problemTags.length) {
        var tags = h('div', { class: 'tags' });
        p.problemTags.forEach(function (t) { tags.appendChild(h('span', { class: 'pill', text: t.tagInfo })); });
        body.appendChild(tags);
      }

      body.appendChild(h('div', { class: 'section' }, [h('h4', { text: '题目描述' }), h('div', { class: 'prose', text: p.description || '（暂无）' })]));
      body.appendChild(h('div', { class: 'section' }, [h('h4', { text: '输入格式' }), h('div', { class: 'prose', text: p.input || '（暂无）' })]));
      body.appendChild(h('div', { class: 'section' }, [h('h4', { text: '输出格式' }), h('div', { class: 'prose', text: p.output || '（暂无）' })]));

      if (p.problemSamples && p.problemSamples.length) {
        var box = h('div', { class: 'section' }, [h('h4', { text: '样例' })]);
        p.problemSamples.forEach(function (s, i) { box.appendChild(sampleBlock(s, i)); });
        body.appendChild(box);
      }

      left.appendChild(h('div', { class: 'card' }, [body]));

      /* ---- 右栏：编辑器 + 结果 ---- */
      var resultBox = h('div', { class: 'result', id: 'resultBox' });
      var side = h('div', { class: 'detail-side' }, [
        h('div', { class: 'card' }, [
          h('div', { class: 'card-head' }, [
            h('span', { text: '代码' }),
            h('span', { class: 'hint', text: '类名必须是 Main · Ctrl / ⌘ + Enter 提交' })
          ]),
          h('div', { class: 'card-body' }, [editorBlock(id)]),
          h('div', { class: 'card-foot' }, [
            h('button', { class: 'btn btn-primary', id: 'btnSubmit', text: '提交', onclick: function () { doSubmit(id); } }),
            h('button', { class: 'btn', text: '重置模板', onclick: function () {
              var ta = $('code');
              if (!ta) return;
              ta.value = JAVA_TEMPLATE;
              ta.dispatchEvent(new Event('input'));
            } }),
            h('span', { class: 'cell-muted', style: 'margin-left:auto', text: '草稿自动保存到本地' })
          ])
        ]),
        resultBox
      ]);

      app.appendChild(h('div', { class: 'detail-grid' }, [left, side]));
    });
  }

  function sampleBlock(s, i) {
    var copy = h('button', { class: 'btn-link', text: '复制', onclick: function () { copyText(s.input || '', copy); } });
    return h('div', { class: 'sample' }, [
      h('div', { class: 'sample-head' }, [h('span', { text: '样例 ' + (s.sort || i + 1) }), copy]),
      h('div', { class: 'sample-grid' }, [
        h('div', { class: 'sample-cell' }, [h('div', { class: 'label', text: '输入' }), h('pre', { class: 'code-block', text: s.input || '' })]),
        h('div', { class: 'sample-cell' }, [h('div', { class: 'label', text: '输出' }), h('pre', { class: 'code-block', text: s.output || '' })])
      ])
    ]);
  }

  function copyText(text, btn) {
    function done() {
      var old = btn.textContent;
      btn.textContent = '已复制';
      setTimeout(function () { btn.textContent = old; }, 1200);
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done, function () { fallbackCopy(text, done); });
      return;
    }
    fallbackCopy(text, done);
  }

  function fallbackCopy(text, done) {
    var ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.select();
    try { document.execCommand('copy'); done(); } catch (e) { toast('复制失败', 'err'); }
    ta.parentNode.removeChild(ta);
  }

  /* ---- 代码编辑器：行号 + Tab 缩进 + localStorage 草稿 ---- */
  function editorBlock(pid) {
    var ta = h('textarea', { id: 'code', spellcheck: 'false', placeholder: '// 在这里写你的 Java 代码' });
    var gutter = h('div', { class: 'gutter' });
    // 高亮层：套一层 <code> 方便整体替换内容
    var hlCode = h('code');
    var hl = h('pre', { class: 'highlight' }, [hlCode]);
    var area = h('div', { class: 'code-area' }, [hl, ta]);

    var wrap = h('div', { class: 'editor' }, [
      h('div', { class: 'editor-bar' }, [
        h('span', { class: 'lang', text: 'Java' }),
        h('span', { class: 'hint', text: 'Main.java · Tab 缩进 / Shift + Tab 反缩进' })
      ]),
      h('div', { class: 'editor-body' }, [gutter, area])
    ]);

    var draft = null;
    try { draft = localStorage.getItem(codeKey(pid)); } catch (e) { /* 忽略 */ }
    ta.value = draft || JAVA_TEMPLATE;

    // 行号栏只在行数变化时重建。每次输入都清空重来 = 创建 N 个 div（N = 行数），
    // 代码写长了每个按键都付一次这个成本，是输入卡顿的来源之一
    var lastLines = -1;
    function sync() {
      var n = ta.value.split('\n').length;
      if (n !== lastLines) {
        lastLines = n;
        clear(gutter);
        var frag = document.createDocumentFragment();
        for (var i = 1; i <= n; i++) frag.appendChild(h('div', { text: String(i) }));
        gutter.appendChild(frag);   // 一次性插入，只重排一次
      }
      gutter.scrollTop = ta.scrollTop;
    }

    // localStorage 是同步写、跑在主线程上，每敲一个键写一次必然掉帧。
    // 输入路径走防抖，失焦 / 提交前再强制落盘。
    function save() { try { localStorage.setItem(codeKey(pid), ta.value); } catch (e) { /* 忽略 */ } }
    var saveTimer = null;
    function saveSoon() { clearTimeout(saveTimer); saveTimer = setTimeout(save, 500); }
    function flushSave() { clearTimeout(saveTimer); save(); }

    // 高亮层就是用户实际看到的文字（textarea 的文字是透明的），
    // 所以它必须跟着输入同帧更新 —— 一旦防抖，打字就会"延迟一拍"。
    // 用 rAF 而不是 setTimeout：同一帧内的多次输入只重绘一次，
    // 且更新落在浏览器绘制之前，视觉上感觉不到延迟。
    function paint() {
      paintScheduled = false;
      hlCode.replaceChildren(highlightJava(ta.value));   // 一次替换，比清空再逐个 append 快得多
      hl.scrollTop = ta.scrollTop;
      hl.scrollLeft = ta.scrollLeft;
    }
    var paintScheduled = false;
    function schedulePaint() {
      if (paintScheduled) return;
      paintScheduled = true;
      requestAnimationFrame(paint);
    }

    sync();
    paint();
    ta.addEventListener('input', function () { sync(); saveSoon(); schedulePaint(); });
    ta.addEventListener('blur', flushSave);
    ta.addEventListener('scroll', function () {
      gutter.scrollTop = ta.scrollTop;
      hl.scrollTop = ta.scrollTop;
      hl.scrollLeft = ta.scrollLeft;
    });
    /* Tab 缩进 / Shift + Tab 反缩进
       光标没选中内容：只在光标处增删一档 4 个空格
       选中了内容：整行整行地加减，选区保持住（多行调整一次到位） */
    var INDENT = '    ';
    function indent(dir) {
      var s = ta.selectionStart, t = ta.selectionEnd, v = ta.value;

      if (s === t) {
        if (dir > 0) {
          ta.value = v.slice(0, s) + INDENT + v.slice(t);
          ta.selectionStart = ta.selectionEnd = s + INDENT.length;
        } else {
          // 反缩进：光标前是空格就最多删 4 个，是制表符就删一个，都不是就什么都不做
          var head = v.slice(v.lastIndexOf('\n', s - 1) + 1, s);
          var cut = 0;
          if (/\t$/.test(head)) cut = 1;
          else if (/ +$/.test(head)) cut = Math.min(4, / +$/.exec(head)[0].length);
          if (!cut) return;
          ta.value = v.slice(0, s - cut) + v.slice(s);
          ta.selectionStart = ta.selectionEnd = s - cut;
        }
        sync(); save();
        return;
      }

      // 选中范围扩到整行；但选区正好停在行首时，不把这一行算进去
      var from = v.lastIndexOf('\n', s - 1) + 1;
      var nl = v.indexOf('\n', t);
      var to = (t > from && v.charAt(t - 1) === '\n') ? t - 1 : (nl < 0 ? v.length : nl);
      var block = v.slice(from, to).split('\n').map(function (ln) {
        if (dir > 0) return INDENT + ln;
        var m = /^(\t| {1,4})/.exec(ln);
        return m ? ln.slice(m[1].length) : ln;
      }).join('\n');
      ta.value = v.slice(0, from) + block + v.slice(to);
      // 调整完选区仍盖住整块（行首到最后一个换行符），可以接着再按 Tab
      ta.selectionStart = from;
      ta.selectionEnd = from + block.length;
      sync(); save();
    }

    ta.addEventListener('keydown', function (e) {
      if (e.key === 'Tab') {
        e.preventDefault();
        indent(e.shiftKey ? -1 : 1);
      } else if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        var btn = $('btnSubmit');
        if (btn && !btn.disabled) btn.click();
      }
    });

    // 切题目前先把上一份草稿落盘，避免防抖窗口内的改动跟着编辑器一起被丢掉
    if (editorCtx && editorCtx.flush) editorCtx.flush();
    editorCtx = { el: ta, save: save, flush: flushSave };
    return wrap;
  }

  function doSubmit(problemId) {
    if (!api.isLogin()) { pendingSubmit = problemId; showLoginModal(false); return; }
    var ta = $('code');
    var code = ta ? ta.value.trim() : '';
    if (!code) { toast('代码不能为空', 'err'); return; }
    if (code.indexOf('class Main') < 0) toast('沙箱固定编译 Main.java，类名需为 Main', 'err');

    var btn = $('btnSubmit');
    if (btn) btn.disabled = true;
    if (editorCtx) editorCtx.save();

    var box = $('resultBox');
    if (box) { clear(box); box.appendChild(spinnerRow('已提交，正在判题…')); }

    api.submit({ problemId: problemId, submitLanguage: 'Java', code: code })
      .then(function (vo) {
        if (!box) return;
        if (isFinal(vo)) { clear(box); drawResult(box, vo); }
        else pollInto(box, vo.id, 0, null);
      })
      .catch(function (e) {
        toast(e.message, 'err');
        if (box) clear(box);
      })
      .then(function () { var b = $('btnSubmit'); if (b) b.disabled = false; });
  }

  /* ===================== 提交记录列表（提交记录页 / 用户主页共用） ===================== */

  // 题目候选：题库分页接口本身就支持 title 模糊搜，直接用
  function searchProblems(kw) {
    return api.problemPage({ pageNO: 1, pageSize: 8, title: kw }).then(function (page) {
      return ((page && page.data) || []).map(function (p) {
        return { id: p.id, label: p.title || ('题目 ' + p.id), hint: '#' + p.id + ' · ' + (DIFF[p.difficulty] || '未知') };
      });
    });
  }

  // 用户候选：后端没有「按用户名搜索」的接口，排行榜是唯一能拿到用户名的地方，
  // 所以拉一次全量缓存起来，之后在本地过滤。
  // 用户量涨到几百以上时，正确做法是后端加一个 GET /user/search?keyword=
  var userListPromise = null;
  function searchUsers(kw) {
    if (!userListPromise) {
      userListPromise = api.rank({ pageNO: 1, pageSize: 500 }).then(function (page) {
        return ((page && page.data) || []).map(function (u) { return { id: u.id, label: u.username, hint: '#' + u.id }; });
      }).catch(function () {
        userListPromise = null;     // 失败别把拒绝状态缓存住，下次还能重试
        throw new Error('用户列表加载失败');
      });
    }
    var low = kw.toLowerCase();
    return userListPromise.then(function (all) {
      return all.filter(function (u) { return String(u.label || '').toLowerCase().indexOf(low) >= 0; }).slice(0, 8);
    });
  }

  function submitRowTr(s) {
    return h('tr', { class: 'row-link', onclick: function () {
      submitTitleCache[s.id] = s.title || '';
      location.hash = '#/submit/' + s.id;
    } }, [
      h('td', {}, [statusBadge(s.status)]),
      h('td', {}, [
        h('div', { class: 'cell-stack' }, [
          h('a', { href: '#/problem/' + s.problemId, text: s.title || ('题目 ' + s.problemId) }),
          h('span', { class: 'sub', text: statusOf(s.status)[0] })
        ])
      ]),
      h('td', {}, [
        h('div', { class: 'cell-stack' }, [
          h('span', { text: s.username || ('用户 ' + s.userId) }),
          h('span', { class: 'sub', text: s.submitLanguage || '' })
        ])
      ]),
      h('td', { class: 'num', text: s.timeUsed != null ? s.timeUsed + ' ms' : '—' }),
      h('td', { class: 'num', text: s.memoryUsed != null ? s.memoryUsed + ' KB' : '—' }),
      h('td', { class: 'col-time', text: relTime(s.submitTime) })
    ]);
  }

  // 把「表格 + 分页条」画进 box。筛选条件、页码、每页条数全从 state 读，
  // 切页时只重画 box，外面的筛选栏原样留着，输入到一半的内容不会被清掉。
  // 返回这一次的 rows，调用方可以拿去做别的事（比如回填筛选框里的名字）。
  function drawSubmitList(box, state, emptyText) {
    clear(box);
    box.appendChild(spinnerRow());
    return api.submitPage({
      pageNO: state.pageNO, pageSize: state.pageSize,
      problemId: state.problemId, userId: state.userId
    }).then(function (page) {
      var rows = (page && page.data) || [];
      clear(box);
      if (!rows.length) {
        box.appendChild(emptyState(emptyText || '暂无提交记录', ''));
        return rows;
      }
      box.appendChild(tableEl(['状态', '题目', '用户', ['耗时', 'num'], ['内存', 'num'], '提交时间'], rows.map(submitRowTr)));
      box.appendChild(pagerBar(page, state, function () { drawSubmitList(box, state, emptyText); }));
      return rows;
    }).catch(function (e) {
      clear(box);
      box.appendChild(h('div', { class: 'state error' }, [
        h('div', { class: 'state-title', text: '提交记录加载失败' }),
        h('div', { class: 'state-text', text: e.message || '' }),
        h('button', { class: 'btn-link', text: '重试', onclick: function () { drawSubmitList(box, state, emptyText); } })
      ]));
      return [];
    });
  }

  /* ===================== 页面：提交记录 ===================== */

  function renderSubmits(ctx) {
    var app = ctx.app;
    // 路由在渲染前会往 app 里放一行「加载中…」，页面自己要负责清掉它
    clear(app);

    var state = {
      pageNO: 1, pageSize: PAGE_SIZE,
      problemId: ctx.query.problemId ? parseInt(ctx.query.problemId, 10) : null,
      userId: ctx.query.userId ? parseInt(ctx.query.userId, 10) : null
    };

    app.appendChild(pageHead('提交记录', '可按题目 / 用户筛选，点击任意一行查看完整判题结果'));

    var listBox = h('div');
    var card = h('div', { class: 'card' });

    // 筛选栏：选中的条件直接回填进输入框，所以「筛没筛」一眼就能看出来，
    // 不用再另摆一行文字说明。下拉里点一下就查，没有额外的「查询」按钮；
    // 把输入框清空则撤掉这一条筛选
    var pPicker = searchPicker('搜索题目', searchProblems,
      function (it) { applyFilter(it.id, state.userId); },
      function () { if (state.problemId) applyFilter(null, state.userId); });
    var uPicker = searchPicker('搜索用户', searchUsers,
      function (it) { applyFilter(state.problemId, it.id); },
      function () { if (state.userId) applyFilter(state.problemId, null); });
    var clearBtn = h('button', {
      class: 'btn btn-sm', style: 'margin-left:auto', text: '清除筛选',
      onclick: function () { applyFilter(null, null); }
    });

    card.appendChild(h('div', { class: 'toolbar' }, [pPicker.el, uPicker.el, clearBtn]));
    card.appendChild(listBox);
    app.appendChild(card);

    if (state.problemId) pPicker.input.value = '#' + state.problemId;
    if (state.userId) uPicker.input.value = '#' + state.userId;
    syncClearBtn();

    // 筛选条件写回 hash：刷新不丢、链接能直接分享。
    // 这里用 replaceState 而不是给 location.hash 赋值——后者会触发 hashchange 把整个页面
    // 推倒重建（输入框内容、滚动位置全没），而且条件没变时根本不触发，点了没反应
    function syncHash() {
      var q = [];
      if (state.problemId) q.push('problemId=' + state.problemId);
      if (state.userId) q.push('userId=' + state.userId);
      try {
        history.replaceState(null, '', '#/submits' + (q.length ? '?' + q.join('&') : ''));
      } catch (e) {
        // 直接双击 index.html（file://）时浏览器不允许改地址栏，筛选照常生效，只是链接没法分享
      }
    }

    function syncClearBtn() {
      clearBtn.hidden = !(state.problemId || state.userId);
    }

    function applyFilter(problemId, userId) {
      state.problemId = problemId || null;
      state.userId = userId || null;
      state.pageNO = 1;               // 换了条件，页号重新从 1 开始
      if (!state.problemId) pPicker.input.value = '';
      if (!state.userId) uPicker.input.value = '';
      syncHash();
      syncClearBtn();
      return load();
    }

    // URL 里带筛选进来时输入框里只有 #id，从结果行里把标题 / 用户名捞回来补上
    function fillNames(rows) {
      rows.forEach(function (s) {
        if (state.problemId && s.problemId === state.problemId && s.title) pPicker.input.value = s.title;
        if (state.userId && s.userId === state.userId && s.username) uPicker.input.value = s.username;
      });
    }

    function load() {
      var filtered = !!(state.problemId || state.userId);
      return drawSubmitList(listBox, state, filtered ? '当前筛选条件下还没有提交' : '暂无提交记录')
        .then(function (rows) { fillNames(rows); return rows; });
    }

    return load();
  }

  /* ===================== 页面：排行榜 ===================== */

  var rankState = { pageNO: 1, pageSize: PAGE_SIZE };

  function renderRank(ctx) {
    var app = ctx.app;
    rankState = { pageNO: 1, pageSize: PAGE_SIZE };

    function load() { return api.rank({ pageNO: rankState.pageNO, pageSize: rankState.pageSize }).then(draw); }

    function draw(page) {
      clear(app);
      app.appendChild(pageHead('排行榜', '按通过题数排序，点击用户名查看主页'));

      var card = h('div', { class: 'card' });
      var rows = (page && page.data) || [];
      if (!rows.length) {
        card.appendChild(emptyState('暂无排名数据', ''));
        app.appendChild(card);
        return;
      }

      var base = (rankState.pageNO - 1) * rankState.pageSize;
      var trs = rows.map(function (u, i) {
        return h('tr', { class: 'row-link', onclick: function () { location.hash = '#/user/' + u.id; } }, [
          h('td', { class: 'col-no mono', text: String(base + i + 1) }),
          h('td', {}, [
            h('div', { style: 'display:flex;align-items:center;gap:8px' }, [
              avatar(null, u.username, false),
              h('a', { href: '#/user/' + u.id, text: u.username })
            ])
          ]),
          h('td', { class: 'num', text: String(u.totalAccept || 0) }),
          h('td', { class: 'num', text: String(u.totalSubmit || 0) }),
          h('td', { class: 'num', text: rate(u.totalSubmit, u.totalAccept) })
        ]);
      });
      card.appendChild(tableEl([['#', 'col-no'], '用户', ['通过', 'num'], ['提交', 'num'], ['通过率', 'num']], trs));
      card.appendChild(pagerBar(page, rankState, load));
      app.appendChild(card);
    }

    return load();
  }

  /* ===================== 页面：我的 ===================== */

  function renderProfile(ctx) {
    var app = ctx.app;
    return Promise.all([api.me(), api.myInfo(), api.acceptCount()]).then(function (r) {
      var user = r[0] || {}, info = r[1] || {}, accept = r[2] || 0;
      clear(app);
      app.appendChild(pageHead('我的', ''));

      /* --- 左：个人卡片 --- */
      var av = avatar(user.avatar, user.username, true);
      av.addEventListener('click', function () { var f = $('avatarInput'); if (f) f.click(); });
      $('avatarInput').onchange = handleAvatarChange;

      var left = h('div', { class: 'card profile-card' }, [
        h('div', { class: 'card-body' }, [
          av,
          h('div', { class: 'profile-name', text: user.username || '' }),
          user.mood ? h('div', { class: 'profile-mood', text: user.mood }) : null,
          h('div', { class: 'profile-stat' }, [
            h('div', {}, [h('b', { text: String(accept || 0) }), h('span', { text: '通过' })]),
            h('div', {}, [h('b', { text: String(user.totalSubmit || 0) }), h('span', { text: '提交' })]),
            h('div', {}, [h('b', { text: rate(user.totalSubmit, accept) }), h('span', { text: '通过率' })])
          ]),
          h('div', { class: 'profile-hint', text: '点击头像上传（jpg / png，≤ 2MB）' })
        ])
      ]);

      /* --- 右：资料表单 --- */
      var ageF = field('年龄', user.age, '未填写', 'number');
      var genderF = genderField(user.gender);
      var moodF = field('个性签名', user.mood, '一句话介绍自己');

      var btnUser = h('button', { class: 'btn btn-primary', text: '保存基本信息', onclick: function () {
        var age = ageF.input.value.trim();
        btnUser.disabled = true;
        api.updateUser({
          age: age === '' ? null : parseInt(age, 10),
          gender: genderF.input.value || null,
          mood: moodF.input.value.trim() || null
        }).then(function () { toast('基本信息已保存', 'ok'); })
          .catch(function (e) { toast(e.message, 'err'); })
          .then(function () { btnUser.disabled = false; });
      }});

      var basic = h('div', { class: 'card' }, [
        h('div', { class: 'card-head' }, [h('span', { text: '基本信息' })]),
        h('div', { class: 'card-body' }, [
          h('div', { class: 'form-grid' }, [ageF.wrap, genderF.wrap, moodF.wrap]),
          h('div', { style: 'margin-top:14px' }, [kvRow('邮箱', user.email), kvRow('总提交', String(user.totalSubmit || 0))])
        ]),
        h('div', { class: 'card-foot' }, [
          btnUser,
          h('span', { class: 'cell-muted', style: 'margin-left:auto', text: '邮箱不可修改' })
        ])
      ]);

      var nameF = field('真实姓名', info.realName, '未填写');
      var phoneF = field('手机号', info.phone, '11 位数字', 'tel');
      var gitF = field('GitHub', info.github, '用户名或主页链接');
      var schoolF = field('学校', info.school, '未填写');
      var majorF = field('专业', info.major, '未填写');

      var btnInfo = h('button', { class: 'btn btn-primary', text: '保存详细资料', onclick: function () {
        btnInfo.disabled = true;
        api.updateInfo({
          realName: nameF.input.value.trim() || null,
          phone: phoneF.input.value.trim() || null,
          github: gitF.input.value.trim() || null,
          school: schoolF.input.value.trim() || null,
          major: majorF.input.value.trim() || null
        }).then(function () { toast('详细资料已保存', 'ok'); })
          .catch(function (e) { toast(e.message, 'err'); })
          .then(function () { btnInfo.disabled = false; });
      }});

      var detail = h('div', { class: 'card' }, [
        h('div', { class: 'card-head' }, [h('span', { text: '详细资料' })]),
        h('div', { class: 'card-body' }, [
          h('div', { class: 'form-grid' }, [nameF.wrap, phoneF.wrap, gitF.wrap, schoolF.wrap, majorF.wrap]),
          h('div', { style: 'margin-top:14px' }, [kvRow('注册时间', fmtTime(info.createTime))])
        ]),
        h('div', { class: 'card-foot' }, [btnInfo])
      ]);

      var acCard = h('div', { class: 'card' }, [
        h('div', { class: 'card-head' }, [h('span', { text: '我的 AC' })]),
        h('div', { class: 'card-body' }, [h('div', { class: 'pills' }, [h('span', { class: 'pill', text: '加载中…' })])])
      ]);
      api.myAcceptProblems().then(function (ids) {
        fillProblemPills(acCard, ids || [], '还没有通过的题目');
      }).catch(function () {
        fillProblemPills(acCard, [], '加载失败');
      });

      var logoutCard = h('div', { class: 'card' }, [
        h('div', { class: 'card-foot' }, [
          h('button', { class: 'btn btn-danger', text: '退出登录', onclick: doLogout })
        ])
      ]);

      app.appendChild(h('div', { class: 'profile-grid' }, [left, h('div', {}, [basic, detail, acCard, logoutCard])]));
    });
  }

  function fillProblemPills(card, ids, emptyText) {
    var body = card.querySelector('.card-body');
    if (!body) return;
    clear(body);
    if (!ids.length) {
      body.appendChild(h('div', { class: 'cell-muted', text: emptyText }));
      return;
    }
    var box = h('div', { class: 'pills' });
    ids.forEach(function (id) {
      box.appendChild(h('span', {
        class: 'pill accepted', text: '#' + id,
        onclick: function () { location.hash = '#/problem/' + id; }
      }));
    });
    body.appendChild(box);
  }

  /* ---- 头像上传：POST /user/me/load，字段名 file ---- */
  function handleAvatarChange() {
    var input = $('avatarInput');
    var f = input.files && input.files[0];
    input.value = '';
    if (!f) return;
    if (f.size > 2 * 1024 * 1024) { toast('图片不能超过 2MB', 'err'); return; }
    if (!/^image\/(jpeg|png)$/.test(f.type)) { toast('仅支持 jpg / png 格式', 'err'); return; }

    toast('上传中…');
    api.uploadAvatar(f).then(function () {
      return api.me();            // 上传接口不返回 URL，回查一次拿新头像
    }).then(function (u) {
      var cached = api.getUser() || {};
      cached.avatar = u && u.avatar ? u.avatar : '';
      api.setUser(cached);
      toast('头像已更新', 'ok');
      router();
    }).catch(function (e) {
      toast(e.message, 'err');
    });
  }

  /* ===================== 页面：他人主页 ===================== */

  function renderUser(ctx) {
    var app = ctx.app;
    var id = parseInt(ctx.seg[1], 10);
    if (!id) return Promise.reject(new Error('用户 id 非法'));

    return Promise.all([api.userProfile(id), api.userAcceptProblems(id)]).then(function (r) {
      var p = r[0] || {}, ids = r[1] || [];
      clear(app);
      app.appendChild(pageHead(p.username || '用户主页', ''));

      var left = h('div', { class: 'card profile-card' }, [
        h('div', { class: 'card-body' }, [
          avatar(p.avatar, p.username, true),
          h('div', { class: 'profile-name', text: p.username || '' }),
          p.mood ? h('div', { class: 'profile-mood', text: p.mood }) : null,
          h('div', { class: 'profile-stat' }, [
            h('div', {}, [h('b', { text: String(p.totalAccept || 0) }), h('span', { text: '通过' })]),
            h('div', {}, [h('b', { text: String(p.totalSubmit || 0) }), h('span', { text: '提交' })]),
            h('div', {}, [h('b', { text: rate(p.totalSubmit, p.totalAccept) }), h('span', { text: '通过率' })])
          ]),
          h('div', { style: 'margin-top:14px;text-align:left' }, [
            kvRow('学校', p.school),
            kvRow('专业', p.major),
            p.github
              ? h('div', { class: 'kv' }, [
                  h('span', { class: 'k', text: 'GitHub' }),
                  h('a', { class: 'v', href: safeUrl(p.github), target: '_blank', rel: 'noopener noreferrer', text: p.github })
                ])
              : null,
            kvRow('注册时间', fmtTime(p.createTime))
          ])
        ])
      ]);

      var acCard = h('div', { class: 'card' }, [
        h('div', { class: 'card-head' }, [h('span', { text: 'TA 的 AC' })]),
        h('div', { class: 'card-body' }, [])
      ]);
      fillProblemPills(acCard, ids, '还没有通过的题目');

      // TA 的提交记录直接铺在主页上，不用再点一次按钮跳去提交记录页
      var subListBox = h('div');
      var subCard = h('div', { class: 'card' }, [
        h('div', { class: 'card-head' }, [
          h('span', { text: 'TA 的提交记录' }),
          h('span', { class: 'hint', text: '点击任意一行查看完整判题结果' })
        ]),
        subListBox
      ]);
      var subState = { pageNO: 1, pageSize: PAGE_SIZE, problemId: null, userId: id };
      drawSubmitList(subListBox, subState, 'TA 还没有提交过');

      app.appendChild(h('div', { class: 'profile-grid' }, [left, h('div', {}, [acCard, subCard])]));
    });
  }

  /* ===================== 路由 ===================== */

  var VIEWS = {
    problems: { login: false, render: renderProblems },
    problem:  { login: false, render: renderProblemView },
    submit:   { login: true,  render: renderSubmitDetail },
    submits:  { login: true,  render: renderSubmits },
    rank:     { login: true,  render: renderRank },
    profile:  { login: true,  render: renderProfile },
    user:     { login: true,  render: renderUser }
  };

  function parseHash() {
    var raw = (location.hash || '').replace(/^#\/?/, '');
    var query = {};
    var qi = raw.indexOf('?');
    if (qi >= 0) {
      var s = raw.slice(qi + 1);
      raw = raw.slice(0, qi);
      s.split('&').forEach(function (kv) {
        if (!kv) return;
        var p = kv.split('=');
        query[decodeURIComponent(p[0])] = decodeURIComponent(p[1] || '');
      });
    }
    return { seg: raw.split('/').filter(Boolean), query: query };
  }

  function router() {
    closeAllModals();

    var r = parseHash();
    var key = r.seg[0] || 'problems';
    var view = VIEWS[key];
    if (!view) { location.replace('#/problems'); return; }

    renderNav(key);

    var app = $('app');
    clear(app);
    // 题目详情页要放得下「左题面 + 右编辑器」；提交详情页内容也偏横向，一起放宽
    var WIDE_VIEWS = { problem: 1, submit: 1 };
    app.className = 'container' + (WIDE_VIEWS[key] ? ' container-wide' : '');
    window.scrollTo(0, 0);

    // 需要登录的页面：先弹登录框，登录成功后停留原页继续加载
    if (view.login && !api.isLogin()) {
      var btn = h('button', { class: 'btn btn-primary', text: '登录', onclick: function () { showLoginModal(false); } });
      var wrap = h('div', { class: 'card' }, [emptyState('需要登录', '该页面需要登录后查看', btn)]);
      app.appendChild(wrap);
      showLoginModal(false);
      return;
    }

    app.appendChild(spinnerRow('加载中…'));
    view.render({ seg: r.seg, query: r.query, app: app }).catch(function (e) {
      clear(app);
      app.appendChild(h('div', { class: 'card' }, [
        h('div', { class: 'state error' }, [
          h('div', { class: 'state-title', text: '加载失败' }),
          h('div', { class: 'state-text', text: e.message || '请稍后重试' }),
          h('button', { class: 'btn-link', text: '重试', onclick: router })
        ])
      ]));
    });
  }

  /* ===================== 启动 ===================== */

  // token 失效：清本地 → 重新走路由，需要登录的页会自己弹登录框
  api.setUnauthorizedHandler(function () {
    toast('登录已失效，请重新登录', 'err');
    router();
  });

  window.addEventListener('hashchange', router);

  // 用户没手动选过时，系统深浅色变化要跟着变（按钮图标同步刷新）
  if (window.matchMedia) {
    var mq = window.matchMedia('(prefers-color-scheme: dark)');
    var onSchemeChange = function () { if (!storedTheme()) updateThemeButtons(); };
    if (mq.addEventListener) mq.addEventListener('change', onSchemeChange);
    else if (mq.addListener) mq.addListener(onSchemeChange);
  }

  // 首屏补默认 hash 用 replaceState：不触发 hashchange，避免渲染两次
  if (!location.hash) history.replaceState(null, '', '#/problems');
  applyTheme(storedTheme());   // index.html 里的内联脚本已提前上色，这里同步按钮图标
  router();
})();
