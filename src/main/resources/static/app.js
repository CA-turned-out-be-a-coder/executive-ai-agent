let conversationId = null;

const loginScreen = document.getElementById('login-screen');
const mainScreen = document.getElementById('main-screen');
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
const newChatBtn = document.getElementById('new-chat-btn');
const conversationListEl = document.getElementById('conversation-list');
const menuToggleBtn = document.getElementById('menu-toggle-btn');
const sidebarEl = document.getElementById('sidebar');
const sidebarBackdrop = document.getElementById('sidebar-backdrop');
const profileRow = document.getElementById('profile-row');
const profilePopover = document.getElementById('profile-popover');
const profileAvatarImg = document.getElementById('profile-avatar-img');
const profileAvatarFallback = document.getElementById('profile-avatar-fallback');
const profileRowName = document.getElementById('profile-row-name');
const popoverAvatarImg = document.getElementById('popover-avatar-img');
const popoverAvatarFallback = document.getElementById('popover-avatar-fallback');
const popoverName = document.getElementById('popover-name');
const popoverEmail = document.getElementById('popover-email');
const logoutBtn = document.getElementById('logout-btn');

let selectedFileBase64 = null;
let selectedFileMimeType = null;
let selectedFileName = null;

function getCsrfToken() {
    const match = document.cookie.match(/(^| )XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[2]) : null;
}

function getInitials(name, email) {
    const source = (name || email || '?').trim();
    const parts = source.split(/\s+/);
    if (parts.length >= 2) {
        return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return source.slice(0, 2).toUpperCase();
}

async function checkAuth() {
    try {
        const res = await fetch('/api/me', { credentials: 'include', redirect: 'manual' });
        if (res.type === 'opaqueredirect' || res.status === 401) {
            showLogin();
        } else {
            const profile = await res.json();
            renderProfile(profile);
            showChat();
            await loadConversations();
        }
    } catch (e) {
        showLogin();
    }
}

function renderProfile(profile) {
    const initials = getInitials(profile.name, profile.email);
    const firstName = (profile.name || profile.email || 'Account').split(' ')[0];

    if (profile.picture) {
        profileAvatarImg.src = profile.picture;
        profileAvatarImg.classList.remove('hidden');
        profileAvatarFallback.classList.add('hidden');

        popoverAvatarImg.src = profile.picture;
        popoverAvatarImg.classList.remove('hidden');
        popoverAvatarFallback.classList.add('hidden');
    } else {
        profileAvatarFallback.textContent = initials;
        popoverAvatarFallback.textContent = initials;
    }

    profileRowName.textContent = firstName;
    popoverName.textContent = profile.name || 'Account';
    popoverEmail.textContent = profile.email || '';
}

function showLogin() {
    loginScreen.classList.remove('hidden');
    mainScreen.classList.add('hidden');
}

function showChat() {
    loginScreen.classList.add('hidden');
    mainScreen.classList.remove('hidden');
}

/* ---------- Mobile sidebar drawer ---------- */

function openSidebar() {
    sidebarEl.classList.add('open');
    sidebarBackdrop.classList.add('open');
}

function closeSidebar() {
    sidebarEl.classList.remove('open');
    sidebarBackdrop.classList.remove('open');
}

menuToggleBtn.addEventListener('click', () => {
    if (sidebarEl.classList.contains('open')) {
        closeSidebar();
    } else {
        openSidebar();
    }
});

sidebarBackdrop.addEventListener('click', closeSidebar);

/* ---------- Profile popover ---------- */

profileRow.addEventListener('click', (e) => {
    e.stopPropagation();
    profilePopover.classList.toggle('hidden');
});

document.addEventListener('click', (e) => {
    if (!profilePopover.classList.contains('hidden')
        && !profilePopover.contains(e.target)
        && e.target !== profileRow
        && !profileRow.contains(e.target)) {
        profilePopover.classList.add('hidden');
    }
});

logoutBtn.addEventListener('click', () => {
    const formEl = document.createElement('form');
    formEl.method = 'POST';
    formEl.action = '/logout';

    const csrfInput = document.createElement('input');
    csrfInput.type = 'hidden';
    csrfInput.name = '_csrf';
    csrfInput.value = getCsrfToken();
    formEl.appendChild(csrfInput);

    document.body.appendChild(formEl);
    formEl.submit();
});

/* ---------- Conversations ---------- */

async function loadConversations() {
    try {
        const res = await fetch('/conversations', { credentials: 'include' });
        if (!res.ok) return;
        const conversations = await res.json();

        if (conversations.length === 0) {
            await createNewConversation();
            return;
        }

        renderConversationList(conversations);
        await switchToConversation(conversations[0].id);
    } catch (e) {
        console.error('Failed to load conversations', e);
    }
}

function renderConversationList(conversations) {
    conversationListEl.innerHTML = '';
    for (const conv of conversations) {
        const item = document.createElement('div');
        item.className = 'conversation-item' + (conv.id === conversationId ? ' active' : '');
        item.dataset.id = conv.id;

        const title = document.createElement('span');
        title.className = 'title';
        title.textContent = conv.title;
        item.appendChild(title);

        const deleteBtn = document.createElement('button');
        deleteBtn.className = 'delete-btn';
        deleteBtn.type = 'button';
        deleteBtn.textContent = '×';
        deleteBtn.title = 'Delete this chat';
        deleteBtn.addEventListener('click', async (e) => {
            e.stopPropagation();
            await deleteConversation(conv.id);
        });
        item.appendChild(deleteBtn);

        item.addEventListener('click', () => {
            switchToConversation(conv.id);
            if (window.matchMedia('(max-width: 720px)').matches) {
                closeSidebar();
            }
        });
        conversationListEl.appendChild(item);
    }
}

async function createNewConversation() {
    try {
        const res = await fetch('/conversations', {
            method: 'POST',
            credentials: 'include',
            headers: { 'X-XSRF-TOKEN': getCsrfToken() }
        });
        const data = await res.json();
        conversationId = data.id;
        messagesEl.innerHTML = '';
        await refreshConversationList();
        closeSidebar();
    } catch (e) {
        console.error('Failed to create conversation', e);
    }
}

async function refreshConversationList() {
    try {
        const res = await fetch('/conversations', { credentials: 'include' });
        if (!res.ok) return;
        const conversations = await res.json();
        renderConversationList(conversations);
    } catch (e) {
        console.error('Failed to refresh conversation list', e);
    }
}

async function switchToConversation(id) {
    conversationId = id;
    messagesEl.innerHTML = '';

    document.querySelectorAll('.conversation-item').forEach(el => {
        el.classList.toggle('active', el.dataset.id === id);
    });

    try {
        const res = await fetch(`/conversations/${id}/messages`, { credentials: 'include' });
        if (!res.ok) return;
        const history = await res.json();
        for (const msg of history) {
            addMessage(msg.role, msg.content);
        }
    } catch (e) {
        console.error('Failed to load conversation history', e);
    }
}

async function deleteConversation(id) {
    try {
        await fetch(`/conversations/${id}`, {
            method: 'DELETE',
            credentials: 'include',
            headers: { 'X-XSRF-TOKEN': getCsrfToken() }
        });

        if (id === conversationId) {
            await loadConversations();
        } else {
            await refreshConversationList();
        }
    } catch (e) {
        console.error('Failed to delete conversation', e);
    }
}

newChatBtn.addEventListener('click', () => {
    createNewConversation();
});

/* ---------- Message rendering ---------- */

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
    if (!conversationId) return;

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

        await refreshConversationList();
    } catch (err) {
        assistantDiv.textContent = 'Error: ' + err.message;
    }
});

checkAuth();
