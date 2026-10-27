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
