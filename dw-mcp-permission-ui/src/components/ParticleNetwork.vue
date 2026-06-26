<template>
  <canvas ref="canvasRef" class="particle-canvas" />
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const canvasRef = ref(null)
let animId = 0

// 配色：蓝(数据流)、紫(鉴权)、绿(放行)、青(代理)
const COLORS = [
  { r: 56, g: 189, b: 248 },   // 蓝
  { r: 99, g: 102, b: 241 },   // 紫
  { r: 16, g: 185, b: 129 },   // 绿
  { r: 34, g: 211, b: 238 },   // 青
]

onMounted(() => {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  let W, H, cx, cy // cx/cy = 屏幕中心(登录卡片位置)

  const particles = []
  const PARTICLE_COUNT = 80
  const CONNECT_DIST = 200

  // ---- 流动光点 ----
  const flows = []       // 粒子间随机流动
  const converges = []   // 向心汇聚
  const diverges = []    // 向外散射

  function resize() {
    W = canvas.width = window.innerWidth
    H = canvas.height = window.innerHeight
    cx = W / 2
    cy = H / 2
  }

  function initParticles() {
    particles.length = 0
    for (let i = 0; i < PARTICLE_COUNT; i++) {
      const color = COLORS[Math.floor(Math.random() * COLORS.length)]
      particles.push({
        x: Math.random() * W,
        y: Math.random() * H,
        vx: (Math.random() - 0.5) * 0.6,
        vy: (Math.random() - 0.5) * 0.6,
        r: Math.random() * 2 + 1.5,
        color,
        alpha: Math.random() * 0.5 + 0.5,
      })
    }
  }

  // ---- 粒子间随机流动 ----
  function spawnFlow(from, to) {
    flows.push({
      x: from.x, y: from.y,
      tx: to.x, ty: to.y,
      progress: 0,
      color: from.color,
      speed: 0.4 + Math.random() * 0.3,
    })
  }

  // ---- 向心汇聚光点：从随机粒子飞向屏幕中心 ----
  function spawnConverge() {
    const p = particles[Math.floor(Math.random() * particles.length)]
    converges.push({
      sx: p.x, sy: p.y,
      progress: 0,
      color: p.color,
      speed: 0.25 + Math.random() * 0.2,
    })
  }

  // ---- 向外散射光点：从中心飞向随机粒子 ----
  function spawnDiverge(color) {
    const p = particles[Math.floor(Math.random() * particles.length)]
    diverges.push({
      tx: p.x, ty: p.y,
      progress: 0,
      color: color || COLORS[Math.floor(Math.random() * COLORS.length)],
      speed: 0.2 + Math.random() * 0.25,
    })
  }

  let convergeTimer = 0

  function update() {
    // 移动粒子
    for (const p of particles) {
      p.x += p.vx
      p.y += p.vy
      if (p.x < 0 || p.x > W) p.vx *= -1
      if (p.y < 0 || p.y > H) p.vy *= -1
      p.x = Math.max(0, Math.min(W, p.x))
      p.y = Math.max(0, Math.min(H, p.y))
    }

    // 粒子间随机流动
    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        const dx = particles[i].x - particles[j].x
        const dy = particles[i].y - particles[j].y
        const dist = Math.sqrt(dx * dx + dy * dy)
        if (dist < CONNECT_DIST && Math.random() < 0.004) {
          spawnFlow(particles[i], particles[j])
        }
      }
    }

    // 定期生成向心汇聚光点
    convergeTimer++
    if (convergeTimer >= 30) { // 约每0.5秒一个
      convergeTimer = 0
      spawnConverge()
    }

    // 更新粒子间流动
    for (let i = flows.length - 1; i >= 0; i--) {
      flows[i].progress += flows[i].speed * 0.01
      if (flows[i].progress >= 1) flows.splice(i, 1)
    }

    // 更新向心汇聚 — 到达中心后产生散射
    for (let i = converges.length - 1; i >= 0; i--) {
      converges[i].progress += converges[i].speed * 0.01
      if (converges[i].progress >= 1) {
        // 到达中心，爆发2~3个散射光点
        const count = 2 + Math.floor(Math.random() * 2)
        for (let k = 0; k < count; k++) {
          spawnDiverge(converges[i].color)
        }
        converges.splice(i, 1)
      }
    }

    // 更新向外散射
    for (let i = diverges.length - 1; i >= 0; i--) {
      diverges[i].progress += diverges[i].speed * 0.01
      if (diverges[i].progress >= 1) diverges.splice(i, 1)
    }
  }

  // ---- 绘制工具函数 ----
  function drawGlowDot(x, y, radius, color, alpha, coreRadius) {
    const grd = ctx.createRadialGradient(x, y, 0, x, y, radius)
    grd.addColorStop(0, `rgba(${color.r},${color.g},${color.b},${alpha})`)
    grd.addColorStop(0.4, `rgba(${color.r},${color.g},${color.b},${alpha * 0.5})`)
    grd.addColorStop(1, `rgba(${color.r},${color.g},${color.b},0)`)
    ctx.fillStyle = grd
    ctx.beginPath()
    ctx.arc(x, y, radius, 0, Math.PI * 2)
    ctx.fill()
    // 核心白点
    ctx.fillStyle = `rgba(255,255,255,${alpha * 0.9})`
    ctx.beginPath()
    ctx.arc(x, y, coreRadius, 0, Math.PI * 2)
    ctx.fill()
  }

  function draw() {
    ctx.clearRect(0, 0, W, H)

    // 绘制连线
    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        const dx = particles[i].x - particles[j].x
        const dy = particles[i].y - particles[j].y
        const dist = Math.sqrt(dx * dx + dy * dy)
        if (dist < CONNECT_DIST) {
          const alpha = (1 - dist / CONNECT_DIST) * 0.35
          ctx.strokeStyle = `rgba(${particles[i].color.r},${particles[i].color.g},${particles[i].color.b},${alpha})`
          ctx.lineWidth = 1.2
          ctx.beginPath()
          ctx.moveTo(particles[i].x, particles[i].y)
          ctx.lineTo(particles[j].x, particles[j].y)
          ctx.stroke()
        }
      }
    }

    // 绘制粒子
    for (const p of particles) {
      const grd = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.r * 6)
      grd.addColorStop(0, `rgba(${p.color.r},${p.color.g},${p.color.b},${p.alpha * 0.3})`)
      grd.addColorStop(1, `rgba(${p.color.r},${p.color.g},${p.color.b},0)`)
      ctx.fillStyle = grd
      ctx.beginPath()
      ctx.arc(p.x, p.y, p.r * 6, 0, Math.PI * 2)
      ctx.fill()
      ctx.fillStyle = `rgba(${p.color.r},${p.color.g},${p.color.b},${p.alpha})`
      ctx.beginPath()
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
      ctx.fill()
    }

    // 绘制粒子间流动光点
    for (const f of flows) {
      const x = f.x + (f.tx - f.x) * f.progress
      const y = f.y + (f.ty - f.y) * f.progress
      const alpha = Math.sin(f.progress * Math.PI)
      drawGlowDot(x, y, 16, f.color, alpha, 3)
    }

    // 绘制向心汇聚光点 + 拖尾线
    for (const c of converges) {
      // 使用缓动：越靠近中心越慢，产生"被吸入"的视觉
      const ease = 1 - Math.pow(1 - c.progress, 2)
      const x = c.sx + (cx - c.sx) * ease
      const y = c.sy + (cy - c.sy) * ease
      const alpha = Math.sin(c.progress * Math.PI) * 0.95

      // 拖尾线
      const tailLen = 0.15
      const tailEase = 1 - Math.pow(1 - Math.max(0, c.progress - tailLen), 2)
      const tailX = c.sx + (cx - c.sx) * tailEase
      const tailY = c.sy + (cy - c.sy) * tailEase
      const lineGrd = ctx.createLinearGradient(tailX, tailY, x, y)
      lineGrd.addColorStop(0, `rgba(${c.color.r},${c.color.g},${c.color.b},0)`)
      lineGrd.addColorStop(1, `rgba(${c.color.r},${c.color.g},${c.color.b},${alpha * 0.6})`)
      ctx.strokeStyle = lineGrd
      ctx.lineWidth = 2
      ctx.beginPath()
      ctx.moveTo(tailX, tailY)
      ctx.lineTo(x, y)
      ctx.stroke()

      // 光点本体（比普通流动更大）
      drawGlowDot(x, y, 22, c.color, alpha, 4)
    }

    // 绘制向外散射光点 + 拖尾线
    for (const d of diverges) {
      // 使用缓动：刚离开中心时快，到达目标时减速，产生"发射"的视觉
      const ease = Math.pow(d.progress, 2)
      const x = cx + (d.tx - cx) * ease
      const y = cy + (d.ty - cy) * ease
      const alpha = Math.sin(d.progress * Math.PI) * 0.85

      // 拖尾线
      const tailLen = 0.12
      const tailEase = Math.pow(Math.max(0, d.progress - tailLen), 2)
      const tailX = cx + (d.tx - cx) * tailEase
      const tailY = cy + (d.ty - cy) * tailEase
      const lineGrd = ctx.createLinearGradient(tailX, tailY, x, y)
      lineGrd.addColorStop(0, `rgba(${d.color.r},${d.color.g},${d.color.b},0)`)
      lineGrd.addColorStop(1, `rgba(${d.color.r},${d.color.g},${d.color.b},${alpha * 0.5})`)
      ctx.strokeStyle = lineGrd
      ctx.lineWidth = 1.5
      ctx.beginPath()
      ctx.moveTo(tailX, tailY)
      ctx.lineTo(x, y)
      ctx.stroke()

      drawGlowDot(x, y, 18, d.color, alpha, 3)
    }

    // 中心微弱光晕（登录卡片后方的"网关核心"光效）
    const coreGrd = ctx.createRadialGradient(cx, cy, 0, cx, cy, 120)
    coreGrd.addColorStop(0, 'rgba(56,189,248,0.06)')
    coreGrd.addColorStop(0.5, 'rgba(99,102,241,0.03)')
    coreGrd.addColorStop(1, 'rgba(0,0,0,0)')
    ctx.fillStyle = coreGrd
    ctx.beginPath()
    ctx.arc(cx, cy, 120, 0, Math.PI * 2)
    ctx.fill()
  }

  function loop() {
    update()
    draw()
    animId = requestAnimationFrame(loop)
  }

  resize()
  initParticles()
  loop()

  window.addEventListener('resize', () => {
    resize()
    initParticles()
  })

  onUnmounted(() => {
    cancelAnimationFrame(animId)
  })
})
</script>

<style scoped>
.particle-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
}
</style>
