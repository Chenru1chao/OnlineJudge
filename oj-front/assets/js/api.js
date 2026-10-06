/**
 * 后端接口封装（严格对照 oj-server 的 Controller）
 *   UserController    /user      ProblemController /problem
 *   SubmitController  /submit    RankController    /rank
 * 统一响应体 Result<T> = { code: 1 成功 | 0 业务失败, errorMsg, data }
 */
(function (global) {
  'use strict';

  // nginx 已把 /api/xxx 反代到后端 /xxx，页面与接口同域，不需要 CORS。
  // 兜底：直接用 8081 打开时同源留空；双击 index.html（file://）时回落到后端地址。
  var API_BASE = (function () {
    if (location.protocol === 'file:') return 'http://localhost:8081';
    if (location.port === '8081') return '';
    return '/api';
  })();

  var TOKEN_HEADER = 'Authorization';   // 对应 oj.jwt.token-name
  var TOKEN_KEY = 'oj_token';
  var USER_KEY = 'oj_user';

  /* ---------------------- 本地存储 ---------------------- */
  function getToken() {
    try { return localStorage.getItem(TOKEN_KEY) || ''; } catch (e) { return ''; }
  }
  function setToken(token) {
    try { localStorage.setItem(TOKEN_KEY, token); } catch (e) { /* 忽略 */ }
  }
  function getUser() {
    try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null'); } catch (e) { return null; }
  }
  function setUser(user) {
    try { localStorage.setItem(USER_KEY, JSON.stringify(user)); } catch (e) { /* 忽略 */ }
  }
  function clearAuth() {
    try { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY); } catch (e) { /* 忽略 */ }
  }
  function isLogin() { return !!getToken(); }

  /* ---------------------- 请求核心 ---------------------- */
  // 401 的处理交给页面（弹登录 Sheet），这里只登记回调
  var onUnauthorized = null;

  function request(path, options) {
    options = options || {};
    var headers = {};

    var token = getToken();
    // 后端 LoginInterceptor 兼容裸 token 与 Bearer 前缀，这里按 HTTP 惯例发 Bearer
    if (token) headers[TOKEN_HEADER] = 'Bearer ' + token;

    var body = options.body;
    if (body !== undefined && !(body instanceof FormData)) {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(body);
    }

    return fetch(API_BASE + path, {
      method: options.method || 'GET',
      headers: headers,
      body: body
    }).then(function (res) {
      // 未登录：响应体为空，不要解析 body
      if (res.status === 401) {
        clearAuth();
        if (onUnauthorized) onUnauthorized();
        throw new Error('登录已失效，请重新登录');
      }
      return res.text().then(function (text) {
        // 后端没起来时 nginx 返回自己的 502/504 HTML 错误页，不是 Result JSON
        if (!res.ok) {
          var hint = (res.status === 502 || res.status === 503 || res.status === 504)
            ? '，请确认 oj-server 已启动' : '';
          throw new Error('请求失败（HTTP ' + res.status + '）' + hint);
        }
        if (!text) throw new Error('服务无响应');
        var json;
        try { json = JSON.parse(text); } catch (e) {
          throw new Error('响应格式异常（HTTP ' + res.status + '）');
        }
        // Result.code === 1 才是成功；判题结果 WA 也是 code=1，从 data.status 读
        if (json.code !== 1) throw new Error(json.errorMsg || '请求失败');
        return json.data;
      });
    }).catch(function (err) {
      if (err instanceof TypeError) {
        throw new Error('无法连接后端服务，请确认 oj-server 已启动（' + (API_BASE || location.origin) + '）');
      }
      throw err;
    });
  }

  // 对象 → query string，自动丢弃空值
  function qs(params) {
    var usp = new URLSearchParams();
    Object.keys(params || {}).forEach(function (k) {
      var v = params[k];
      if (v !== null && v !== undefined && v !== '') usp.append(k, v);
    });
    var s = usp.toString();
    return s ? '?' + s : '';
  }

  var api = {
    /* ---------------- 用户 ---------------- */
    // POST /user/login → LoginVO {id, username, avatar, token}
    login: function (data) { return request('/user/login', { method: 'POST', body: data }); },
    // POST /user/register → null
    register: function (data) { return request('/user/register', { method: 'POST', body: data }); },
    // POST /user/logout → null（后端空实现，前端清 token 即可）
    logout: function () { return request('/user/logout', { method: 'POST' }); },
    // GET /user → UserVO {id, avatar, username, email, age, gender, mood, totalSubmit}
    me: function () { return request('/user'); },
    // PUT /user → null，body {age, gender, mood}
    updateUser: function (data) { return request('/user', { method: 'PUT', body: data }); },
    // GET /user/me → UserInfoVO {realName, phone, github, school, major, createTime}
    myInfo: function () { return request('/user/me'); },
    // PUT /user/me → null，body {realName, phone, github, school, major}
    updateInfo: function (data) { return request('/user/me', { method: 'PUT', body: data }); },
    // GET /user/me/accept → Long 我的 AC 数
    acceptCount: function () { return request('/user/me/accept'); },
    // POST /user/me/load → null，multipart 字段名必须是 file；不要手动设 Content-Type
    uploadAvatar: function (file) {
      var fd = new FormData();
      fd.append('file', file);
      return request('/user/me/load', { method: 'POST', body: fd });
    },
    // GET /user/accept/problem → Integer[] 我 AC 的题目 id
    myAcceptProblems: function () { return request('/user/accept/problem'); },
    // GET /user/{id}/profile → UserProfileVO
    userProfile: function (id) { return request('/user/' + id + '/profile'); },
    // GET /user/{id}/accept/problem → Integer[]
    userAcceptProblems: function (id) { return request('/user/' + id + '/accept/problem'); },

    /* ---------------- 题目 ----------------（/problem/** 免登录） */
    // 注意：分页参数名是 pageNO（大写 NO），不是 pageNo
    // GET /problem?pageNO&pageSize&difficulty&title → PageResult<ProblemVO>
    problemPage: function (params) { return request('/problem' + qs(params)); },
    // GET /problem/{id} → ProblemDetailVO
    problemDetail: function (id) { return request('/problem/' + id); },

    /* ---------------- 提交 ---------------- */
    // POST /submit → SubmitDetailVO（异步判题，返回时通常是 0 排队中，需要轮询）
    submit: function (data) { return request('/submit', { method: 'POST', body: data }); },
    // GET /submit/{id} → SubmitDetailVO
    submitDetail: function (id) { return request('/submit/' + id); },
    // GET /submit?pageNO&pageSize&problemId&userId → PageResult<SubmitPageVO>
    submitPage: function (params) { return request('/submit' + qs(params)); },

    /* ---------------- 排行榜 ---------------- */
    // GET /rank?pageNO&pageSize → PageResult<RankVO>
    rank: function (params) { return request('/rank' + qs(params)); },

    /* ---------------- 本地状态 ---------------- */
    getToken: getToken,
    setToken: setToken,
    getUser: getUser,
    setUser: setUser,
    clearAuth: clearAuth,
    isLogin: isLogin,
    setUnauthorizedHandler: function (fn) { onUnauthorized = fn; }
  };

  global.api = api;
})(window);
