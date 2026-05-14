<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const canvasRef = ref(null)
let animationId = null
let particles = []
let mouseX = -1000
let mouseY = -1000

const PARTICLE_COUNT = 60

class Particle {
  constructor(w, h) {
    this.reset(w, h, true)
  }
  reset(w, h, randomY) {
    this.x = Math.random() * w
    this.y = randomY ? Math.random() * h : -10
    this.baseVx = (Math.random() - 0.5) * 0.3
    this.baseVy = (Math.random() * 0.2 + 0.05)
    this.size = Math.random() * 2.5 + 0.8
    this.opacity = Math.random() * 0.4 + 0.1
    // purple-blue-cyan hue range
    const hueRand = Math.random()
    this.hue = hueRand < 0.45 ? Math.random() * 30 + 250  // purple-blue (250-280)
             : hueRand < 0.8 ? Math.random() * 30 + 190   // cyan (190-220)
             : Math.random() * 30 + 270                    // deep purple (270-300)
  }
  update(w, h) {
    // gentle drift toward mouse
    const dx = mouseX - this.x
    const dy = mouseY - this.y
    const dist = Math.hypot(dx, dy)
    if (dist < 200) {
      const force = (200 - dist) / 200 * 0.02
      this.x += dx * force
      this.y += dy * force
    }
    this.x += this.baseVx
    this.y += this.baseVy
    if (this.y > h + 10) { this.y = -10; this.x = Math.random() * w }
    if (this.x < -10) this.x = w + 10
    if (this.x > w + 10) this.x = -10
  }
  draw(ctx) {
    ctx.beginPath()
    ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2)
    const gradient = ctx.createRadialGradient(this.x, this.y, 0, this.x, this.y, this.size * 3)
    gradient.addColorStop(0, `hsla(${this.hue}, 70%, 68%, ${this.opacity})`)
    gradient.addColorStop(1, `hsla(${this.hue}, 70%, 68%, 0)`)
    ctx.fillStyle = gradient
    ctx.fill()
  }
}

onMounted(() => {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')

  function resize() {
    canvas.width = window.innerWidth
    canvas.height = window.innerHeight
  }
  resize()
  window.addEventListener('resize', resize)
  document.addEventListener('mousemove', (e) => { mouseX = e.clientX; mouseY = e.clientY })

  for (let i = 0; i < PARTICLE_COUNT; i++) {
    particles.push(new Particle(canvas.width, canvas.height))
  }

  function animate() {
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    for (const p of particles) {
      p.update(canvas.width, canvas.height)
      p.draw(ctx)
    }
    // draw connections
    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        const dist = Math.hypot(particles[i].x - particles[j].x, particles[i].y - particles[j].y)
        if (dist < 120) {
          ctx.beginPath()
          ctx.moveTo(particles[i].x, particles[i].y)
          ctx.lineTo(particles[j].x, particles[j].y)
          const alpha = (1 - dist / 120) * 0.06
          const midHue = (particles[i].hue + particles[j].hue) / 2
          ctx.strokeStyle = `hsla(${midHue}, 60%, 65%, ${alpha})`
          ctx.lineWidth = 0.5
          ctx.stroke()
        }
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
  <canvas ref="canvasRef" class="global-particles" />
</template>

<style scoped>
.global-particles {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}
</style>
