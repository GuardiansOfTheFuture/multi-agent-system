<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const canvasRef = ref(null)
let animationId = null

class Particle {
  constructor(w, h) {
    this.x = Math.random() * w
    this.y = Math.random() * h
    this.vx = (Math.random() - 0.5) * 0.6
    this.vy = (Math.random() - 0.5) * 0.6
    this.size = Math.random() * 2 + 0.5
    this.opacity = Math.random() * 0.5 + 0.15
    const r = Math.random()
    this.hue = r < 0.4 ? Math.random() * 30 + 260  // purple (260-290)
             : r < 0.75 ? Math.random() * 30 + 200 // blue-cyan (200-230)
             : Math.random() * 20 + 170             // teal (170-190)
  }
  update(w, h) {
    this.x += this.vx
    this.y += this.vy
    if (this.x < 0) this.x = w
    if (this.x > w) this.x = 0
    if (this.y < 0) this.y = h
    if (this.y > h) this.y = 0
  }
  draw(ctx) {
    ctx.beginPath()
    ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2)
    ctx.fillStyle = `hsla(${this.hue}, 70%, 65%, ${this.opacity})`
    ctx.fill()
  }
}

class Connection {
  constructor(p1, p2) {
    this.p1 = p1
    this.p2 = p2
  }
  draw(ctx) {
    const dist = Math.hypot(this.p1.x - this.p2.x, this.p1.y - this.p2.y)
    if (dist > 160) return
    const alpha = (1 - dist / 160) * 0.12
    ctx.beginPath()
    ctx.moveTo(this.p1.x, this.p1.y)
    ctx.lineTo(this.p2.x, this.p2.y)
    ctx.strokeStyle = `rgba(160, 130, 240, ${alpha})`
    ctx.lineWidth = 0.5
    ctx.stroke()
  }
}

onMounted(() => {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const particles = []
  const count = 70

  function resize() {
    const rect = canvas.parentElement.getBoundingClientRect()
    canvas.width = rect.width
    canvas.height = rect.height
  }
  resize()
  window.addEventListener('resize', resize)

  for (let i = 0; i < count; i++) {
    particles.push(new Particle(canvas.width, canvas.height))
  }

  function animate() {
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    const w = canvas.width
    const h = canvas.height
    for (let i = 0; i < particles.length; i++) {
      particles[i].update(w, h)
      particles[i].draw(ctx)
    }
    // draw connections between nearby particles
    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        new Connection(particles[i], particles[j]).draw(ctx)
      }
    }
    animationId = requestAnimationFrame(animate)
  }
  animate()

  onUnmounted(() => {
    cancelAnimationFrame(animationId)
    window.removeEventListener('resize', resize)
  })
})
</script>

<template>
  <canvas ref="canvasRef" class="particle-canvas" />
</template>

<style scoped>
.particle-canvas {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}
</style>
