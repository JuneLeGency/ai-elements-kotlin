import * as THREE from 'https://unpkg.com/three@0.160.0/build/three.module.js';

let scene, camera, renderer, clock;
let player = { pos: new THREE.Vector3(0, 1.7, 0), vel: new THREE.Vector3(), yaw: 0, pitch: 0, onGround: true, hp: 100, shield: 100 };
let keys = {}, mouseDown = false, locked = false, gameRunning = false, gameOver = false;
let score = 0, kills = 0, wave = 0;
let ammo = 8, maxAmmo = 8, reloading = false, reloadTimer = 0;
let lastShot = 0, fireRate = 130;
let enemies = [], particles = [], pickups = [];
let gunGroup, gunRecoil = 0, gunSway = { x: 0, y: 0 };
let obstacles = [];

const ARENA = 90;
const EYE = 1.7;

function init() {
  scene = new THREE.Scene();
  scene.fog = new THREE.FogExp2(0x050010, 0.012);
  scene.background = new THREE.Color(0x030008);

  camera = new THREE.PerspectiveCamera(75, innerWidth/innerHeight, 0.1, 1000);
  camera.rotation.order = 'YXZ';
  renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
  renderer.setSize(innerWidth, innerHeight);
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.3;
  document.getElementById('game-container').prepend(renderer.domElement);

  clock = new THREE.Clock();

  scene.add(new THREE.AmbientLight(0x202040, 0.6));
  const dir = new THREE.DirectionalLight(0x8888ff, 0.4);
  dir.position.set(10, 20, 10);
  scene.add(dir);

  buildArena();
  buildGun();
  buildStars();

  wave = 1;
  spawnWave();
  bindEvents();
  updateHUD();
  animate();
}

function buildArena() {
  const grid = new THREE.GridHelper(200, 80, 0x00ffff, 0x181840);
  grid.material.transparent = true; grid.material.opacity = 0.55;
  scene.add(grid);

  const ground = new THREE.Mesh(
    new THREE.PlaneGeometry(200, 200),
    new THREE.MeshStandardMaterial({ color: 0x060012, roughness: 0.4, metalness: 0.6 })
  );
  ground.rotation.x = -Math.PI/2;
  scene.add(ground);

  const wallMat = new THREE.MeshStandardMaterial({ color: 0x0a0018, emissive: 0x1a0033, roughness: 0.5, metalness: 0.4 });
  const edgeMat = new THREE.MeshBasicMaterial({ color: 0x00ffff });
  const H = 8;
  const walls = [
    { pos: [0, H/2, -ARENA], size: [ARENA*2+2, H, 2] },
    { pos: [0, H/2, ARENA], size: [ARENA*2+2, H, 2] },
    { pos: [-ARENA, H/2, 0], size: [2, H, ARENA*2+2] },
    { pos: [ARENA, H/2, 0], size: [2, H, ARENA*2+2] },
  ];
  for (const w of walls) {
    const m = new THREE.Mesh(new THREE.BoxGeometry(...w.size), wallMat);
    m.position.set(...w.pos);
    scene.add(m);
    const edge = new THREE.Mesh(new THREE.BoxGeometry(w.size[0]*0.99, 0.15, w.size[2]*0.99), edgeMat);
    edge.position.set(w.pos[0], H + 0.08, w.pos[2]);
    scene.add(edge);
    obstacles.push({ x: w.pos[0], z: w.pos[2], hx: w.size[0]/2 + 0.5, hz: w.size[2]/2 + 0.5, wall: true });
  }

  const ring = new THREE.Mesh(
    new THREE.TorusGeometry(6, 0.2, 12, 60),
    new THREE.MeshBasicMaterial({ color: 0xff00ff })
  );
  ring.rotation.x = Math.PI/2; ring.position.y = 0.1;
  scene.add(ring);

  const boxMat = new THREE.MeshStandardMaterial({ color: 0x0a0a2a, emissive: 0x001133, roughness: 0.3, metalness: 0.7 });
  for (let i = 0; i < 18; i++) {
    const s = 2 + Math.random()*4;
    const box = new THREE.Mesh(new THREE.BoxGeometry(s, s, s), boxMat.clone());
    let x, z, tries = 0;
    do {
      x = (Math.random()-0.5) * (ARENA*1.6);
      z = (Math.random()-0.5) * (ARENA*1.6);
      tries++;
    } while (Math.hypot(x, z) < 12 && tries < 20);
    box.position.set(x, s/2, z);
    box.rotation.y = Math.random()*Math.PI;
    scene.add(box);
    const e = new THREE.Mesh(new THREE.BoxGeometry(s*1.01, 0.12, s*1.01), new THREE.MeshBasicMaterial({ color: Math.random()>0.5 ? 0x00ffff : 0xff00ff }));
    e.position.copy(box.position); e.position.y = s + 0.06; e.rotation.y = box.rotation.y;
    scene.add(e);
    obstacles.push({ x, z, hx: s/2 + 0.5, hz: s/2 + 0.5 });
  }

  for (let i = 0; i < 8; i++) {
    const a = i/8 * Math.PI*2;
    const r = 55;
    const pillar = new THREE.Mesh(
      new THREE.CylinderGeometry(0.4, 0.6, 20, 8),
      new THREE.MeshStandardMaterial({ color: 0x111133, emissive: 0x00ffff, emissiveIntensity: 0.8 })
    );
    pillar.position.set(Math.cos(a)*r, 10, Math.sin(a)*r);
    scene.add(pillar);
    const light = new THREE.PointLight(0x00ffff, 2, 40);
    light.position.copy(pillar.position).setY(15);
    scene.add(light);
  }
}

function buildStars() {
  const geo = new THREE.BufferGeometry();
  const pos = [];
  for (let i = 0; i < 1500; i++) {
    const r = 400 + Math.random()*300;
    const t = Math.random()*Math.PI*2, p = Math.acos(2*Math.random()-1);
    pos.push(r*Math.sin(p)*Math.cos(t), Math.abs(r*Math.cos(p)), r*Math.sin(p)*Math.sin(t));
  }
  geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  scene.add(new THREE.Points(geo, new THREE.PointsMaterial({ color: 0x88aaff, size: 1.2, sizeAttenuation: true, transparent: true, opacity: 0.8 })));
}

function buildGun() {
  gunGroup = new THREE.Group();
  const mat = new THREE.MeshStandardMaterial({ color: 0x111122, metalness: 0.9, roughness: 0.2 });
  const glowMat = new THREE.MeshBasicMaterial({ color: 0x00ffff });

  const body = new THREE.Mesh(new THREE.BoxGeometry(0.12, 0.18, 0.9), mat);
  body.position.set(0, 0, -0.3);
  gunGroup.add(body);
  const barrel = new THREE.Mesh(new THREE.CylinderGeometry(0.04, 0.04, 0.7, 12), mat);
  barrel.rotation.x = Math.PI/2; barrel.position.set(0, 0.04, -0.9);
  gunGroup.add(barrel);
  const glow = new THREE.Mesh(new THREE.BoxGeometry(0.13, 0.03, 0.5), glowMat);
  glow.position.set(0, 0.1, -0.3);
  gunGroup.add(glow);
  const grip = new THREE.Mesh(new THREE.BoxGeometry(0.08, 0.3, 0.1), mat);
  grip.position.set(0, -0.2, -0.15); grip.rotation.x = 0.3;
  gunGroup.add(grip);

  gunGroup.position.set(0.28, -0.28, -0.55);
  camera.add(gunGroup);
  scene.add(camera);
}

class Enemy {
  constructor(pos) {
    this.group = new THREE.Group();
    const mat = new THREE.MeshStandardMaterial({ color: 0x1a0022, emissive: 0xff0044, emissiveIntensity: 1.2, roughness: 0.3, metalness: 0.6 });
    const glow = new THREE.MeshBasicMaterial({ color: 0xff0066 });
    const body = new THREE.Mesh(new THREE.ConeGeometry(0.7, 1.8, 6), mat);
    body.rotation.x = Math.PI; body.position.y = 1.5;
    this.group.add(body);
    const eye = new THREE.Mesh(new THREE.SphereGeometry(0.18, 12, 12), glow);
    eye.position.set(0, 2.1, -0.4);
    this.group.add(eye);
    const ring = new THREE.Mesh(new THREE.TorusGeometry(0.9, 0.05, 8, 24), glow);
    ring.rotation.x = Math.PI/2; ring.position.y = 0.5;
    this.group.add(ring);
    const light = new THREE.PointLight(0xff0066, 1.5, 12);
    light.position.y = 1.5;
    this.group.add(light);

    this.group.position.copy(pos);
    scene.add(this.group);

    this.hp = 30;
    this.maxHp = 30;
    this.speed = 2.5 + Math.random()*1.5 + wave*0.2;
    this.attackCd = 0;
    this.dead = false;
    this.deathT = 0;
    this.floatPhase = Math.random()*Math.PI*2;
  }
  update(dt) {
    if (this.dead) {
      this.deathT += dt;
      this.group.scale.multiplyScalar(Math.max(0.01, 1 - dt*2));
      this.group.rotation.y += dt*5;
      if (this.deathT > 0.6) scene.remove(this.group);
      return;
    }
    const dir = new THREE.Vector3().subVectors(player.pos, this.group.position);
    dir.y = 0;
    const dist = dir.length();
    dir.normalize();
    this.group.lookAt(player.pos.x, this.group.position.y, player.pos.z);

    if (dist > 2.2) this.group.position.addScaledVector(dir, this.speed * dt);
    this.group.position.y = 0.4 + Math.sin(performance.now()*0.004 + this.floatPhase)*0.15;
    this.attackCd -= dt;
    if (dist < 2.5 && this.attackCd <= 0) {
      this.attackCd = 1.0;
      damagePlayer(10);
      spawnParticles(this.group.position.clone().add(new THREE.Vector3(0,1,0)), 0xff0044, 12, 4);
    }
  }
  hit(dmg) {
    this.hp -= dmg;
    this.group.children[0].material.emissiveIntensity = 3;
    if (this.hp <= 0 && !this.dead) {
      this.dead = true;
      kills++;
      score += 100;
      spawnParticles(this.group.position.clone().add(new THREE.Vector3(0,1.5,0)), 0xff00ff, 30, 8);
      spawnParticles(this.group.position.clone().add(new THREE.Vector3(0,1.5,0)), 0x00ffff, 20, 6);
      if (Math.random() < 0.35) spawnPickup(this.group.position.clone());
      updateHUD();
    }
  }
}

function spawnWave() {
  const n = 6 + wave*2;
  for (let i = 0; i < n; i++) {
    const a = Math.random()*Math.PI*2;
    const r = 30 + Math.random()*45;
    const p = new THREE.Vector3(Math.cos(a)*r, 0, Math.sin(a)*r);
    p.x = THREE.MathUtils.clamp(p.x, -ARENA+3, ARENA-3);
    p.z = THREE.MathUtils.clamp(p.z, -ARENA+3, ARENA-3);
    enemies.push(new Enemy(p));
  }
}

function checkWaveClear() {
  if (enemies.every(e => e.dead)) {
    wave++;
    score += 500;
    player.shield = Math.min(100, player.shield + 30);
    player.hp = Math.min(100, player.hp + 20);
    enemies = enemies.filter(e => !e.dead || e.deathT < 0.6);
    setTimeout(() => { if (gameRunning && !gameOver) spawnWave(); }, 1500);
    updateHUD();
  }
}

function spawnPickup(pos) {
  const g = new THREE.Group();
  const gem = new THREE.Mesh(new THREE.OctahedronGeometry(0.4), new THREE.MeshBasicMaterial({ color: 0x00ff88 }));
  g.add(gem);
  const ring = new THREE.Mesh(new THREE.TorusGeometry(0.7, 0.04, 8, 24), new THREE.MeshBasicMaterial({ color: 0x00ff88 }));
  ring.rotation.x = Math.PI/2;
  g.add(ring);
  g.position.copy(pos).setY(1);
  scene.add(g);
  pickups.push({ mesh: g, t: 0 });
}

function spawnParticles(pos, color, count, speed) {
  const geo = new THREE.BufferGeometry();
  const posArr = [], velArr = [];
  for (let i = 0; i < count; i++) {
    posArr.push(pos.x, pos.y, pos.z);
    const v = new THREE.Vector3(Math.random()-0.5, Math.random()-0.5, Math.random()-0.5).normalize().multiplyScalar(speed*(0.5+Math.random()));
    velArr.push(v.x, v.y, v.z);
  }
  geo.setAttribute('position', new THREE.Float32BufferAttribute(posArr, 3));
  const mat = new THREE.PointsMaterial({ color, size: 0.25, transparent: true, opacity: 1, blending: THREE.AdditiveBlending, depthWrite: false });
  const pts = new THREE.Points(geo, mat);
  scene.add(pts);
  particles.push({ pts, vel: velArr, life: 1.0 });
}

function updateParticles(dt) {
  for (let i = particles.length-1; i >= 0; i--) {
    const p = particles[i];
    p.life -= dt*1.2;
    const pos = p.pts.geometry.attributes.position;
    for (let j = 0; j < pos.count; j++) {
      pos.array[j*3] += p.vel[j*3]*dt;
      pos.array[j*3+1] += p.vel[j*3+1]*dt;
      pos.array[j*3+2] += p.vel[j*3+2]*dt;
      p.vel[j*3+1] -= 6*dt;
    }
    pos.needsUpdate = true;
    p.pts.material.opacity = Math.max(0, p.life);
    if (p.life <= 0) {
      scene.remove(p.pts);
      p.pts.geometry.dispose();
      p.pts.material.dispose();
      particles.splice(i, 1);
    }
  }
}

function shoot() {
  if (gameOver || !gameRunning || reloading) return;
  const now = performance.now();
  if (now - lastShot < fireRate) return;
  if (ammo <= 0) { startReload(); return; }
  lastShot = now;
  ammo--;
  gunRecoil = 1;
  updateHUD();

  const mf = document.getElementById('muzzle-flash');
  mf.style.opacity = '1';
  setTimeout(() => mf.style.opacity = '0', 60);

  const light = new THREE.PointLight(0x00ffff, 8, 15);
  light.position.copy(camera.position);
  scene.add(light);
  setTimeout(() => scene.remove(light), 50);

  const ray = new THREE.Raycaster();
  ray.setFromCamera(new THREE.Vector2(0,0), camera);
  let best = null, bestDist = Infinity;
  for (const e of enemies) {
    if (e.dead) continue;
    const target = e.group.position.clone().add(new THREE.Vector3(0,1.5,0));
    const rayTo = new THREE.Vector3().subVectors(target, ray.ray.origin);
    const proj = rayTo.dot(ray.ray.direction);
    if (proj < 0) continue;
    const closest = ray.ray.origin.clone().addScaledVector(ray.ray.direction, proj);
    const dist = closest.distanceTo(target);
    if (dist < 1.2 && proj < bestDist) { bestDist = proj; best = e; }
  }
  if (best) {
    best.hit(25);
    showHitMarker();
    spawnParticles(ray.ray.origin.clone().addScaledVector(ray.ray.direction, bestDist), 0x00ffff, 10, 5);
  } else {
    const wallHits = ray.intersectObjects(scene.children, false);
    if (wallHits.length && wallHits[0].distance < 100) {
      spawnParticles(wallHits[0].point, 0x00ffff, 8, 4);
    }
  }
  if (ammo <= 0) startReload();
}

function startReload() {
  if (reloading || ammo === maxAmmo) return;
  reloading = true;
  reloadTimer = 1.2;
  document.getElementById('ammo').textContent = '...';
}

function showHitMarker() {
  const hm = document.getElementById('hit-marker');
  hm.style.opacity = '1';
  setTimeout(() => hm.style.opacity = '0', 120);
}

function damagePlayer(dmg) {
  if (gameOver) return;
  let remaining = dmg;
  if (player.shield > 0) {
    const absorbed = Math.min(player.shield, remaining*0.6);
    player.shield -= absorbed;
    remaining -= absorbed;
  }
  player.hp -= remaining;
  const v = document.getElementById('damage-vignette');
  v.style.opacity = '1';
  setTimeout(() => v.style.opacity = '0', 300);
  if (player.hp <= 0) {
    player.hp = 0;
    endGame();
  }
  updateHUD();
}

function endGame() {
  gameOver = true;
  document.getElementById('final-score').textContent = score;
  document.getElementById('gameover').style.display = 'flex';
  if (document.pointerLockElement) document.exitPointerLock();
}

function resetGame() {
  enemies.forEach(e => scene.remove(e.group));
  enemies = [];
  particles.forEach(p => scene.remove(p.pts));
  particles = [];
  pickups.forEach(p => scene.remove(p.mesh));
  pickups = [];
  player.pos.set(0, EYE, 0);
  player.vel.set(0,0,0);
  player.hp = 100; player.shield = 100;
  player.yaw = 0; player.pitch = 0;
  score = 0; kills = 0; wave = 1;
  ammo = maxAmmo; reloading = false;
  gameOver = false;
  document.getElementById('gameover').style.display = 'none';
  updateHUD();
  spawnWave();
  gameRunning = true;
  requestPointerLock();
}

function updateHUD() {
  document.getElementById('hp-fill').style.width = Math.max(0, player.hp) + '%';
  document.getElementById('shield-val').textContent = Math.max(0, Math.round(player.shield));
  document.getElementById('score').textContent = score;
  document.getElementById('kills').textContent = 'KILLS: ' + kills;
  if (!reloading) document.getElementById('ammo').textContent = ammo;
}

function requestPointerLock() {
  renderer.domElement.requestPointerLock();
}

function bindEvents() {
  const overlay = document.getElementById('overlay');
  overlay.addEventListener('click', () => {
    if (!gameRunning && !gameOver) {
      gameRunning = true;
      overlay.style.display = 'none';
      requestPointerLock();
    }
  });
  document.getElementById('gameover').addEventListener('click', resetGame);

  document.addEventListener('pointerlockchange', () => {
    locked = !!document.pointerLockElement;
  });
  document.addEventListener('mousemove', (e) => {
    if (!locked || !gameRunning || gameOver) return;
    player.yaw -= e.movementX * 0.0022;
    player.pitch -= e.movementY * 0.0022;
    player.pitch = THREE.MathUtils.clamp(player.pitch, -Math.PI/2 + 0.05, Math.PI/2 - 0.05);
    gunSway.x = THREE.MathUtils.lerp(gunSway.x, e.movementX * 0.0003, 0.3);
    gunSway.y = THREE.MathUtils.lerp(gunSway.y, e.movementY * 0.0003, 0.3);
  });
  document.addEventListener('mousedown', (e) => {
    if (e.button === 0) { mouseDown = true; if (gameRunning && !gameOver) shoot(); }
  });
  document.addEventListener('mouseup', (e) => { if (e.button === 0) mouseDown = false; });
  document.addEventListener('keydown', (e) => {
    keys[e.code] = true;
    if (e.code === 'KeyR') startReload();
    if (e.code === 'Space') e.preventDefault();
  });
  document.addEventListener('keyup', (e) => { keys[e.code] = false; });
  window.addEventListener('resize', () => {
    camera.aspect = innerWidth/innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(innerWidth, innerHeight);
  });
}

function updatePlayer(dt) {
  const speed = keys['ShiftLeft'] ? 14 : 8;
  const forward = new THREE.Vector3(-Math.sin(player.yaw), 0, -Math.cos(player.yaw));
  const right = new THREE.Vector3(-forward.z, 0, forward.x);
  const move = new THREE.Vector3();
  if (keys['KeyW']) move.add(forward);
  if (keys['KeyS']) move.sub(forward);
  if (keys['KeyD']) move.add(right);
  if (keys['KeyA']) move.sub(right);
  if (move.lengthSq() > 0) move.normalize().multiplyScalar(speed);

  player.vel.x = move.x;
  player.vel.z = move.z;
  if (keys['Space'] && player.onGround) {
    player.vel.y = 8;
    player.onGround = false;
  }
  player.vel.y -= 22 * dt;

  const newPos = player.pos.clone().addScaledVector(player.vel, dt);
  newPos.y = Math.max(EYE, newPos.y);
  if (newPos.y === EYE) { player.vel.y = 0; player.onGround = true; }

  // 碰撞检测
  newPos.x = THREE.MathUtils.clamp(newPos.x, -ARENA+1, ARENA-1);
  newPos.z = THREE.MathUtils.clamp(newPos.z, -ARENA+1, ARENA-1);
  for (const o of obstacles) {
    if (Math.abs(newPos.x - o.x) < o.hx && Math.abs(newPos.z - o.z) < o.hz) {
      const dx = newPos.x - o.x, dz = newPos.z - o.z;
      if (Math.abs(dx)/o.hx > Math.abs(dz)/o.hz) {
        newPos.x = o.x + Math.sign(dx) * o.hx;
      } else {
        newPos.z = o.z + Math.sign(dz) * o.hz;
      }
    }
  }
  player.pos.copy(newPos);

  camera.position.copy(player.pos);
  camera.rotation.set(player.pitch, player.yaw, 0);

  // 枪晃动
  const t = performance.now() * 0.001;
  let bobX = 0, bobY = 0;
  if (move.lengthSq() > 0 && player.onGround) {
    bobX = Math.sin(t*12) * 0.01;
    bobY = Math.abs(Math.cos(t*12)) * 0.015;
  }
  gunRecoil = Math.max(0, gunRecoil - dt*8);
  gunGroup.position.set(0.28 + bobX + gunSway.x, -0.28 + bobY - gunSway.y + gunRecoil*0.08, -0.55 + gunRecoil*0.15);
  gunGroup.rotation.x = gunRecoil * 0.2;

  // 换弹
  if (reloading) {
    reloadTimer -= dt;
    if (reloadTimer <= 0) {
      reloading = false;
      ammo = maxAmmo;
      updateHUD();
    }
  }

  // 拾取
  for (let i = pickups.length-1; i >= 0; i--) {
    const p = pickups[i];
    p.t += dt;
    p.mesh.rotation.y += dt*2;
    p.mesh.position.y = 1 + Math.sin(p.t*3)*0.2;
    if (p.mesh.position.distanceTo(player.pos) < 1.5) {
      player.shield = Math.min(100, player.shield + 25);
      score += 50;
      spawnParticles(p.mesh.position, 0x00ff88, 15, 5);
      scene.remove(p.mesh);
      pickups.splice(i, 1);
      updateHUD();
    }
  }
}

function animate() {
  requestAnimationFrame(animate);
  const dt = Math.min(clock.getDelta(), 0.05);

  if (gameRunning && !gameOver) {
    updatePlayer(dt);
    for (const e of enemies) e.update(dt);
    checkWaveClear();
    if (mouseDown) shoot();
  }
  updateParticles(dt);

  renderer.render(scene, camera);
}

init();
