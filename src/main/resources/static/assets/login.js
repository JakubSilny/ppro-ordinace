(() => {
  const params = new URLSearchParams(location.search);
  const message = document.getElementById('login-message');
  const texts = {
    error: 'Nesprávné jméno nebo heslo.',
    logout: 'Byli jste odhlášeni.',
    expired: 'Přihlášení vypršelo. Přihlaste se znovu.',
    locked: 'Příliš mnoho špatných pokusů. Účet je na 5 minut zablokovaný.',
  };
  for (const [key, text] of Object.entries(texts)) {
    if (params.has(key)) {
      message.textContent = text;
      message.hidden = false;
      message.classList.toggle('error', key === 'error' || key === 'locked');
    }
  }
  const fillToken = () => {
    const cookie = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='));
    document.getElementById('csrf').value = cookie ? decodeURIComponent(cookie.split('=')[1]) : '';
  };
  fillToken();
  document.querySelector('form').addEventListener('submit', fillToken);
})();
