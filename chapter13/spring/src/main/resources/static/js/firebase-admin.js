import { initializeApp } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-app.js";
import { getAnalytics } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-analytics.js";
import { getMessaging, register, onRegistered } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-messaging.js";

(() => {
    const page = document.getElementById('firebase-page');
    if (!page) return;

    const firebaseConfig = {
        apiKey: "AIzaSyBL76SbtdZG92qdTsfQtkPGpgCfIiiZZRM",
        authDomain: "springboot-book-b8c94.firebaseapp.com",
        databaseURL: "https://springboot-book-b8c94-default-rtdb.asia-southeast1.firebasedatabase.app",
        projectId: "springboot-book-b8c94",
        storageBucket: "springboot-book-b8c94.firebasestorage.app",
        messagingSenderId: "1061064391098",
        appId: "1:1061064391098:web:5067998f204972c6864583",
        measurementId: "G-8XDN4EHHQK"
    };

    const vapidKey = "BBtmssWa_N7XBMf-0qVFNDJVt4fm8U5lajfJlcZ6F3rgyL2Y1ais-5Xh7-cPcuL5mu7Blxz6Tp9BprjWEa2oitk";

    const app = initializeApp(firebaseConfig);
    const analytics = getAnalytics(app);
    const messaging = getMessaging(app);

    const tokenButton = document.getElementById('issue-fcm-token');
    const tokenInput = document.getElementById('fcm-token');

    if (tokenButton && tokenInput) {
        onRegistered(messaging, (fid) => {
            tokenInput.value = fid;
            alert('FCM 등록이 완료되어 FID를 받았습니다.');
        });

        tokenButton.addEventListener('click', async () => {
            tokenButton.disabled = true;
            tokenInput.value = '';

            try {
                const permission = await Notification.requestPermission();

                if (permission !== 'granted') {
                    alert('FCM 토큰을 받으려면 알림 권한을 허용해야 합니다.');
                    return;
                }

                const registration = await navigator.serviceWorker.register(
                    '/firebase-messaging-sw.js'
                );

                await register(messaging, {
                    vapidKey: vapidKey,
                    serviceWorkerRegistration: registration
                });
            } catch (error) {
                console.error('FCM 등록 실패:', error);
                alert('FCM 등록 실패: ' + error.message);
            } finally {
                tokenButton.disabled = false;
            }
        });
    }

    const status = document.getElementById('request-status');
    const result = document.getElementById('request-result');
    const buttons = page.querySelectorAll('button[type="submit"]');
    let busy = false;

    async function request(url, options = {}) {
        const response = await fetch(url, {
            ...options,
            credentials: 'same-origin',
            cache: 'no-store',
            headers: { Accept: 'application/json', ...options.headers }
        });
        if (response.status === 401) {
            throw new Error('로그인이 만료되었습니다. 로그인 화면에서 다시 로그인하세요.');
        }
        if (response.status === 403) {
            throw new Error('관리자 권한 또는 CSRF 검증에 실패했습니다. 다시 로그인하세요.');
        }
        const contentType = response.headers.get('content-type') || '';
        if (response.redirected || !contentType.includes('application/json')) {
            throw new Error('JSON 응답을 받지 못했습니다. 로그인 상태와 서버 설정을 확인하세요.');
        }
        const data = await response.json();
        if (!response.ok) {
            const fields = Object.entries(data.errors || {})
                .map(([field, message]) => field + ': ' + message).join('\n');
            throw new Error(
                'HTTP ' + response.status + '\n'
                + (data.message || '요청에 실패했습니다.')
                + (fields ? '\n' + fields : '')
            );
        }
        return { data, status: response.status };
    }

    page.querySelectorAll('.firebase-form').forEach(form => {
        form.addEventListener('submit', async event => {
            event.preventDefault();
            if (busy || !form.reportValidity()) return;
            busy = true;
            buttons.forEach(button => { button.disabled = true; });
            page.setAttribute('aria-busy', 'true');
            status.className = 'text-secondary';
            status.textContent = '처리 중';
            result.textContent = '';

            try {
                const values = Object.fromEntries(new FormData(form).entries());
                const options = { method: form.dataset.method };
                let url = form.action;

                if (form.dataset.pathField) {
                    url += '/' + encodeURIComponent(values[form.dataset.pathField]);
                }
                if (options.method === 'POST') {
                    const { data: csrf } = await request(page.dataset.csrfUrl);
                    options.headers = {
                        'Content-Type': 'application/json',
                        [csrf.headerName]: csrf.token
                    };
                    options.body = JSON.stringify(values);
                }

                const reply = await request(url, options);
                status.className = 'text-success';
                status.textContent = '완료 · HTTP ' + reply.status;
                result.textContent = JSON.stringify(reply.data, null, 2);

                if (form.id === 'message-form') {
                    document.getElementById('document-id').value = reply.data.id;
                }
            } catch (error) {
                status.className = 'text-danger';
                status.textContent = '실패';
                result.textContent = error.message || '서버 연결을 확인하세요.';
            } finally {
                busy = false;
                buttons.forEach(button => { button.disabled = false; });
                page.removeAttribute('aria-busy');
            }
        });
    });
})();