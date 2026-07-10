const conversationId = crypto.randomUUID();

const loginScreen = document.getElementById('login-screen');
const chatScreen = document.getElementById('chat-screen');
const messagesEl = document.getElementById('messages');
const form = document.getElementById('chat-form');
const input = document.getElementById('message-input');
const micBtn = document.getElementById('mic-btn');
const attachBtn = document.getElementById('attach-btn');
const fileInput = document.getElementById('image-input');
const filePreviewWrap = document.getElementById('file-preview-wrap');
const imagePreview = document.getElementById('image-preview');
const docPreview = document.getElementById('doc-preview');
const docPreviewName = document.getElementById('doc-preview-name');
const removeFileBtn = document.getElementById('remove-image-btn');

let selectedFileBase64 = null;
let selectedFileMimeType = null;
let selectedFileName = null;

function getCsrfToken() {
    const match = document.cookie.match(/(^| )XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[2]) : null;
}

async function checkAuth() {
    try {
        const res = await fetch('/api/me', { credentials: 'include', redirect: 'manual' });
        if (res.type === 'opaqueredirect' || res.status === 401) {
            showLogin();
        } else {
            showChat();
        }
    } catch (e) {
        showLogin();
    }
}

function showLogin() {
    loginScreen.classList.remove('hidden');
    chatScreen.classList.add('hidden');
}

function showChat() {
    loginScreen.classList.add('hidden');
    chatScreen.classList.remove('hidden');
}

function addMessage(role, text, attachment) {
    const div = document.createElement('div');
    div.className = `msg ${role}`;
    div.textContent = text;
    if (attachment && attachment.isImage) {
        const img = document.createElement('img');
        img.src = attachment.dataUrl;
        img.className = 'attached-image';
        img.alt = 'Attached image';
        div.appendChild(img);
    } else if (attachment) {
        const chip = document.createElement('div');
        chip.textContent = `📄 ${attachment.fileName}`;
        chip.style.marginTop = '8px';
        chip.style.fontSize = '13px';
        chip.style.opacity = '0.85';
        div.appendChild(chip);
    }
    messagesEl.appendChild(div);
    messagesEl.scrollTop = messagesEl.scrollHeight;
    return div;
}

/* ---------- Voice input ---------- */

const SpeechRecognitionImpl = window.SpeechRecognition || window.webkitSpeechRecognition;

if (SpeechRecognitionImpl) {
    const recognition = new SpeechRecognitionImpl();
    recognition.lang = 'en-US';
    recognition.interimResults = false;
    recognition.maxAlternatives = 1;

    let isRecording = false;

    micBtn.addEventListener('click', () => {
        if (isRecording) {
            recognition.stop();
            return;
        }
        try {
            recognition.start();
        } catch (e) {
            // recognition may already be starting; ignore
        }
    });

    recognition.addEventListener('start', () => {
        isRecording = true;
        micBtn.classList.add('recording');
    });

    recognition.addEventListener('end', () => {
        isRecording = false;
        micBtn.classList.remove('recording');
    });

    recognition.addEventListener('result', (event) => {
        const transcript = event.results[0][0].transcript;
        input.value = input.value ? `${input.value} ${transcript}` : transcript;
        input.focus();
    });

    recognition.addEventListener('error', () => {
        isRecording = false;
        micBtn.classList.remove('recording');
    });
} else {
    micBtn.style.display = 'none';
}

/* ---------- File attach ---------- */

attachBtn.addEventListener('click', () => {
    fileInput.click();
});

fileInput.addEventListener('change', () => {
    const file = fileInput.files[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = () => {
        const dataUrl = reader.result;
        selectedFileBase64 = dataUrl.split(',')[1];
        selectedFileMimeType = file.type || guessMimeTypeFromName(file.name);
        selectedFileName = file.name;

        if (selectedFileMimeType.startsWith('image/')) {
            imagePreview.src = dataUrl;
            imagePreview.classList.remove('hidden');
            docPreview.classList.add('hidden');
        } else {
            docPreviewName.textContent = file.name;
            docPreview.classList.remove('hidden');
            imagePreview.classList.add('hidden');
        }
        filePreviewWrap.classList.remove('hidden');
    };
    reader.readAsDataURL(file);
});

function guessMimeTypeFromName(name) {
    const lower = name.toLowerCase();
    if (lower.endsWith('.pdf')) return 'application/pdf';
    if (lower.endsWith('.xlsx')) return 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
    if (lower.endsWith('.xls')) return 'application/vnd.ms-excel';
    if (lower.endsWith('.docx')) return 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
    if (lower.endsWith('.csv')) return 'text/csv';
    if (lower.endsWith('.txt')) return 'text/plain';
    return 'application/octet-stream';
}

removeFileBtn.addEventListener('click', () => {
    clearSelectedFile();
});

function clearSelectedFile() {
    selectedFileBase64 = null;
    selectedFileMimeType = null;
    selectedFileName = null;
    fileInput.value = '';
    filePreviewWrap.classList.add('hidden');
    imagePreview.classList.add('hidden');
    docPreview.classList.add('hidden');
}

/* ---------- Chat submit ---------- */

form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const message = input.value.trim();
    if (!message && !selectedFileBase64) return;

    const hasFile = !!selectedFileBase64;
    const isImage = hasFile && selectedFileMimeType.startsWith('image/');

    const attachment = hasFile
        ? (isImage
            ? { isImage: true, dataUrl: `data:${selectedFileMimeType};base64,${selectedFileBase64}` }
            : { isImage: false, fileName: selectedFileName })
        : null;

    addMessage('user', message, attachment);
    input.value = '';

    const assistantDiv = addMessage('assistant', '');

    const requestBody = hasFile
        ? {
            conversationId,
            message,
            fileBase64: selectedFileBase64,
            fileMimeType: selectedFileMimeType,
            fileName: selectedFileName
        }
        : { conversationId, message };
    const endpoint = hasFile ? '/chat/file/stream' : '/chat/stream';

    clearSelectedFile();

    try {
        const response = await fetch(endpoint, {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                'X-XSRF-TOKEN': getCsrfToken()
            },
            body: JSON.stringify(requestBody)
        });

        if (!response.ok || !response.body) {
            assistantDiv.textContent = 'Error: could not reach Dolly.';
            return;
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let fullText = '';

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });

            const events = buffer.split('\n\n');
            buffer = events.pop();

            for (const event of events) {
                const dataLines = event
                    .split('\n')
                    .filter(line => line.startsWith('data:'))
                    .map(line => line.slice(5));
                fullText += dataLines.join('\n');
            }

            assistantDiv.textContent = fullText;
            messagesEl.scrollTop = messagesEl.scrollHeight;
        }
    } catch (err) {
        assistantDiv.textContent = 'Error: ' + err.message;
    }
});

checkAuth();
