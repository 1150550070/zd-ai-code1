<template>
  <a-layout-footer class="footer">
    <div class="footer-content">
      <div class="footer-info">
        <h3 class="platform-name">慧搭 AI 零代码平台</h3>
        <p class="platform-desc">让创意无界，让开发更简单</p>
        <p class="copyright">
          © 2025 慧搭AI应用生成平台 · 智能化应用开发新体验
        </p>
      </div>
    </div>
  </a-layout-footer>
</template>

<script setup lang="ts">
// 无需额外的响应式数据
</script>

<style scoped>
.footer {
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.1) 0%, rgba(102, 126, 234, 0.15) 100%);
  backdrop-filter: blur(20px);
  text-align: center;
  padding: 40px 20px;
  margin-top: 60px;
  border-top: 1px solid rgba(102, 126, 234, 0.2);
  position: relative;
}

.footer::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(102, 126, 234, 0.5), transparent);
}

.footer-content {
  max-width: 1200px;
  margin: 0 auto;
}

.footer-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.platform-name {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  background: linear-gradient(135deg, #409EFF 0%, #667eea 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.platform-desc {
  margin: 0;
  color: #667eea;
  font-size: 16px;
  font-weight: 500;
}

.copyright {
  margin: 0;
  color: #8892b0;
  font-size: 14px;
  opacity: 0.8;
}

@media (max-width: 768px) {
  .footer {
    padding: 30px 15px;
  }

  .platform-name {
    font-size: 20px;
  }

  .platform-desc {
    font-size: 14px;
  }
}
</style>
