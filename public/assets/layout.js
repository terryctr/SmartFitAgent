(function () {
  const NAV = [
    { label: '概览', items: [
      { id: 'dashboard', icon: '⊞', label: '训练总览' },
    ]},
    { label: '训练', items: [
      { id: 'workout',  icon: '💪', label: '训练日志' },
      { id: 'planner',  icon: '📅', label: '训练计划' },
      { id: 'pomodoro', icon: '⏱', label: '训练计时' },
    ]},
    { label: '身体 & 健康', items: [
      { id: 'measurements', icon: '📏', label: '身体数据' },
      { id: 'sleep',        icon: '🌙', label: '睡眠记录' },
      { id: 'health',       icon: '❤️', label: '健康顾问' },
    ]},
    { label: '营养', items: [
      { id: 'nutrition', icon: '🥗', label: '营养追踪' },
      { id: 'notes',     icon: '📝', label: '饮食日记' },
    ]},
    { label: 'AI 功能', items: [
      { id: 'tutor',   icon: '🤖', label: 'AI 教练' },
      { id: 'writing', icon: '✍️', label: 'AI 日志' },
      { id: 'courses', icon: '📚', label: '健身课程' },
      { id: 'vocab',   icon: '🏋️', label: '动作库' },
    ]},
    { label: '分析 & 成就', items: [
      { id: 'analytics',    icon: '📈', label: '数据分析' },
      { id: 'quiz',         icon: '🧠', label: '体能测试' },
      { id: 'challenges',   icon: '🏆', label: '挑战赛' },
      { id: 'achievements', icon: '🎖️', label: '成就' },
      { id: 'community',    icon: '🌐', label: '社区' },
      { id: 'mistakes',     icon: '⚠️', label: '风险纠正' },
    ]},
    { label: '账号', items: [
      { id: 'profile', icon: '👤', label: '个人档案' },
    ]},
  ];

  const pageId = document.body.dataset.page || '';

  const nav = NAV.map(g => `
    <div class="nav-group">
      <div class="nav-group-label">${g.label}</div>
      ${g.items.map(item => `
        <a href="/pages/${item.id}.html" class="nav-item ${item.id === pageId ? 'active' : ''}">
          <span class="nav-icon">${item.icon}</span>
          <span>${item.label}</span>
        </a>`).join('')}
    </div>`).join('');

  const sidebar = document.createElement('aside');
  sidebar.className = 'sidebar';
  sidebar.innerHTML = `
    <div class="sidebar-brand">
      <div class="brand-icon">SF</div>
      <div>
        <div class="brand-name">SmartFit</div>
        <div class="brand-sub">AI 健身教练</div>
      </div>
    </div>
    <nav class="sidebar-nav">${nav}</nav>
    <div class="sidebar-footer">
      <a href="/pages/profile.html" class="nav-item ${pageId === 'profile' ? 'active' : ''}">
        <span class="nav-icon">⚙️</span><span>设置档案</span>
      </a>
    </div>`;

  document.body.prepend(sidebar);
})();
