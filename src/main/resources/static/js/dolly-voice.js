/**
 * Dolly Voice Output — browser SpeechSynthesis wrapper
 * Handles: voice list async loading, Chrome long-utterance cutoff bug
 * (via sentence-boundary chunking + queued playback), clean text
 * extraction, per-message speak/stop toggling, auto-stop on new message.
 */

const DollyVoice = (() => {
  let voiceList = [];
  let voicesReady = false;
  let activeButton = null;
  let queue = [];
  let isSpeaking = false;
  let cancelledByUser = false;

  function loadVoices() {
    voiceList = window.speechSynthesis.getVoices();
    voicesReady = voiceList.length > 0;
  }
  loadVoices();
  if ('onvoiceschanged' in window.speechSynthesis) {
    window.speechSynthesis.onvoiceschanged = loadVoices;
  }

  function pickVoice() {
    if (!voicesReady) loadVoices();
    const preferredNames = ['Samantha', 'Google US English', 'Alex', 'Daniel'];
    for (const name of preferredNames) {
      const match = voiceList.find(v => v.name.includes(name));
      if (match) return match;
    }
    return voiceList.find(v => v.lang && v.lang.startsWith('en')) || null;
  }

  function cleanText(raw) {
    return raw
      .replace(/\*\*(.*?)\*\*/g, '$1')
      .replace(/\*(.*?)\*/g, '$1')
      .replace(/[_#`]/g, '')
      .replace(/\[(.*?)\]\(.*?\)/g, '$1')
      .replace(/\n{2,}/g, '. ')
      .replace(/\n/g, ' ')
      .trim();
  }

  function chunkText(text, maxLen = 200) {
    const sentences = text.match(/[^.!?]+[.!?]+(\s|$)/g) || [text];
    const chunks = [];
    let current = '';

    for (const sentence of sentences) {
      if ((current + sentence).length > maxLen && current.length > 0) {
        chunks.push(current.trim());
        current = sentence;
      } else {
        current += sentence;
      }
    }
    if (current.trim()) chunks.push(current.trim());
    return chunks;
  }

  function speakNextInQueue() {
    if (cancelledByUser || queue.length === 0) {
      isSpeaking = false;
      if (activeButton) activeButton.classList.remove('speaking');
      activeButton = null;
      return;
    }

    const chunk = queue.shift();
    const utterance = new SpeechSynthesisUtterance(chunk);
    utterance.rate = 1.0;
    utterance.pitch = 1.0;

    const voice = pickVoice();
    if (voice) utterance.voice = voice;

    utterance.onend = () => speakNextInQueue();
    utterance.onerror = () => speakNextInQueue();

    window.speechSynthesis.speak(utterance);
  }

  function speak(rawText, buttonEl) {
    if (activeButton === buttonEl && isSpeaking) {
      stop();
      return;
    }

    stop();

    cancelledByUser = false;
    const text = cleanText(rawText);
    if (!text) return;

    queue = chunkText(text);
    isSpeaking = true;
    activeButton = buttonEl;
    buttonEl.classList.add('speaking');

    speakNextInQueue();
  }

  function stop() {
    cancelledByUser = true;
    window.speechSynthesis.cancel();
    queue = [];
    isSpeaking = false;
    if (activeButton) activeButton.classList.remove('speaking');
    activeButton = null;
  }

  return { speak, stop };
})();

function attachSpeakButton(messageContainerEl, messageText) {
  const btn = document.createElement('button');
  btn.className = 'speak-btn';
  btn.setAttribute('aria-label', 'Read message aloud');
  btn.innerHTML = '🔊';
  btn.onclick = () => DollyVoice.speak(messageText, btn);
  messageContainerEl.appendChild(btn);
  return btn;
}
