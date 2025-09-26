<template>
  <a-layout class="basic-layout">
    <!-- 背景装饰 -->
    <div class="background-decoration">
      <div class="bg-circle circle-1"></div>
      <div class="bg-circle circle-2"></div>
      <div class="bg-circle circle-3"></div>
    </div>

    <!-- 顶部导航栏 -->
    <GlobalHeader />

    <!-- 主要内容区域 -->
    <a-layout-content class="main-content">
      <div class="content-wrapper">
        <router-view />
      </div>
    </a-layout-content>

    <!-- 底部版权信息 -->
    <GlobalFooter />
  </a-layout>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'

// 滚动动态效果
let scrollY = 0

const handleScroll = () => {
  scrollY = window.scrollY
  const backgroundDecoration = document.querySelector('.background-decoration') as HTMLElement
  const circles = document.querySelectorAll('.bg-circle') as NodeListOf<HTMLElement>

  if (backgroundDecoration) {
    // 背景视差效果
    backgroundDecoration.style.transform = `translateY(${scrollY * 0.3}px)`
  }

  // 圆圈视差效果
  circles.forEach((circle, index) => {
    const speed = 0.1 + (index * 0.05)
    circle.style.transform = `translateY(${scrollY * speed}px) rotate(${scrollY * 0.1}deg)`
  })
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})
</script>

<style scoped>
.basic-layout {
  min-height: 100vh;
  background: linear-gradient(135deg,
    #f8fafc 0%,
    #e2e8f0 15%,
    #cbd5e1 30%,
    #a5b4fc 45%,
    #c7d2fe 60%,
    #e0e7ff 75%,
    #f1f5f9 100%
  );
  background-attachment: fixed;
  background-size: 400% 400%;
  animation: gentleGradient 25s ease infinite;
  position: relative;
  overflow-x: hidden;
}

@keyframes gentleGradient {
  0% { background-position: 0% 50%; }
  25% { background-position: 100% 0%; }
  50% { background-position: 100% 100%; }
  75% { background-position: 0% 100%; }
  100% { background-position: 0% 50%; }
}

.background-decoration {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
}

.bg-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(165, 180, 252, 0.08);
  backdrop-filter: blur(15px);
  animation: float 8s ease-in-out infinite;
  transition: transform 0.1s ease-out;
  will-change: transform;
}

.circle-1 {
  width: 300px;
  height: 300px;
  top: 10%;
  right: -150px;
  animation-delay: 0s;
}

.circle-2 {
  width: 200px;
  height: 200px;
  bottom: 20%;
  left: -100px;
  animation-delay: 2s;
}

.circle-3 {
  width: 150px;
  height: 150px;
  top: 50%;
  right: 10%;
  animation-delay: 4s;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0px) rotate(0deg) scale(1);
  }
  33% {
    transform: translateY(-15px) rotate(120deg) scale(1.05);
  }
  66% {
    transform: translateY(-25px) rotate(240deg) scale(0.95);
  }
}

.main-content {
  width: 100%;
  padding: 0;
  background: none;
  margin: 0;
  position: relative;
  z-index: 1;
}

.content-wrapper {
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(25px);
  border-radius: 24px;
  margin: 20px;
  padding: 20px;
  box-shadow: 0 12px 40px rgba(165, 180, 252, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.3);
  min-height: calc(100vh - 200px);
  transition: transform 0.1s ease-out;
  will-change: transform;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .content-wrapper {
    margin: 10px;
    padding: 15px;
    border-radius: 15px;
  }

  .bg-circle {
    display: none; /* 在移动设备上隐藏装饰圆圈 */
  }
}

@media (max-width: 480px) {
  .content-wrapper {
    margin: 5px;
    padding: 10px;
    border-radius: 10px;
  }
}
</style>
