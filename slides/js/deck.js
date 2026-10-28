Reveal.initialize({
  width: 1280,
  height: 720,
  margin: 0.04,
  center: false,
  hash: true,
  controlsLayout: "edges",
  slideNumber: "c/t",
  transition: "fade",
  transitionSpeed: "fast",
  plugins: [RevealHighlight, RevealNotes],
  highlight: {
    beforeHighlight: (hljs) => hljs.registerLanguage("wirespec", wirespecLanguage),
  },
});

// The footer shows the Flock wordmark and the label of the current slide, e.g. "step-07 · ..."
const footerLabel = document.querySelector(".deck-footer .label");
const showLabel = ({ currentSlide }) => {
  footerLabel.textContent = currentSlide.dataset.label ?? "";
};
Reveal.on("ready", showLabel);
Reveal.on("slidechanged", showLabel);

// Transitions: every slide's content rises out of a soft blur with a brief cyan/magenta fringe,
// slides with data-glitch="strong" get the full RGB glitch, and a faint yellow glow drifts to a
// new spot in the background. The CSS skips all of it when the OS asks for reduced motion.
const ghostFilter = (id, dx, dy) => `
  <filter id="${id}" x="-5%" y="-5%" width="110%" height="110%" color-interpolation-filters="sRGB">
    <feFlood flood-color="#00ffff" result="cyan" />
    <feComposite in="cyan" in2="SourceAlpha" operator="in" result="cyanMask" />
    <feOffset in="cyanMask" dx="${-dx}" dy="${-dy}" result="cyanShift" />
    <feFlood flood-color="#ff0066" result="magenta" />
    <feComposite in="magenta" in2="SourceAlpha" operator="in" result="magentaMask" />
    <feOffset in="magentaMask" dx="${dx}" dy="${dy}" result="magentaShift" />
    <feBlend in="cyanShift" in2="magentaShift" mode="screen" result="ghosts" />
    <feColorMatrix in="ghosts" type="matrix" values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 0.55 0" result="fadedGhosts" />
    <feMerge>
      <feMergeNode in="fadedGhosts" />
      <feMergeNode in="SourceGraphic" />
    </feMerge>
  </filter>`;

document.getElementById("glitch-filters").innerHTML = `<defs>${[
  ghostFilter("rgb-1", 6, 0),
  ghostFilter("rgb-2", 4, 1),
  ghostFilter("rgb-3", 2, 0),
  ghostFilter("rgb-4", 14, 0),
  ghostFilter("rgb-5", 8, 2),
].join("")}</defs>`;

const overlay = document.createElement("div");
overlay.className = "glitch-overlay";
document.querySelector(".reveal").appendChild(overlay);

const restart = (element, className, duration) => {
  element.classList.remove(className);
  void element.offsetWidth;
  element.classList.add(className);
  setTimeout(() => element.classList.remove(className), duration);
};

const glitchBar = (i) =>
  `<i style="top: ${Math.random() * 100}%; left: ${Math.random() * 70}%; width: ${10 + Math.random() * 30}%; background: ${i % 2 ? "#ff0066" : "#00ffff"}"></i>`;

const animateIn = (slide) => {
  if (slide.dataset.glitch === "strong") {
    overlay.innerHTML = Array.from({ length: 6 }, (_, i) => glitchBar(i)).join("");
    restart(slide, "glitch-strong", 650);
    restart(overlay, "on", 650);
  } else {
    restart(slide, "glitch-in", 320);
  }
  restart(slide, "enter", 1100);
};

const glowSpots = [
  [80, 15],
  [18, 25],
  [72, 82],
  [30, 70],
  [88, 48],
  [45, 12],
  [12, 85],
  [60, 40],
];

const moveGlow = (slide) => {
  const { h = 0, v = 0 } = Reveal.getIndices(slide);
  const [x, y] = glowSpots[(h + v) % glowSpots.length];
  const viewport = document.querySelector(".reveal-viewport");
  viewport.style.setProperty("--glow-x", `${x}%`);
  viewport.style.setProperty("--glow-y", `${y}%`);
};

const transition = ({ currentSlide }) => {
  animateIn(currentSlide);
  moveGlow(currentSlide);
};
Reveal.on("ready", transition);
Reveal.on("slidechanged", transition);
