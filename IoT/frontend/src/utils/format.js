/**
 * Unix 타임스탬프를 HH:mm 형식의 문자열로 변환합니다.
 * @param {string|number} timeStr - 타임스탬프 또는 ISO 날짜 문자열
 * @returns {string} 포맷팅된 시간 문자열
 */
export const formatTime = (timeStr) => {
  if (!timeStr) return '';
  try {
    const d = new Date(typeof timeStr === 'number' ? timeStr * 1000 : timeStr);
    return `${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`;
  } catch (e) {
    return '';
  }
};

/**
 * Date 객체를 YYYY.MM.DD (요일) 형식의 문자열로 변환합니다.
 * @param {Date} date - Date 객체
 * @returns {string} 포맷팅된 날짜 문자열
 */
export const formatDateDisplay = (date) => {
  const days = ['일요일', '월요일', '화요일', '수요일', '목요일', '금요일', '토요일'];
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  const dayName = days[date.getDay()];
  return `${y}.${m}.${d} (${dayName})`;
};

/**
 * 보낸 사람 이름이나 제목에 따라 일관된 색상 클래스를 반환합니다.
 * 결정론적(deterministic) 해시 함수를 사용하여 항상 같은 이름에는 같은 색상이 부여됩니다.
 * @param {string} sender - 보낸 사람 이름 또는 식별자
 * @returns {string} Tailwind CSS 클래스 문자열
 */
export const getSenderColor = (sender) => {
  let hash = 0;
  const str = (sender || '가족').trim();

  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash = hash & hash;
  }

  hash = Math.abs(hash + str.length * 7919);

  const colorPalettes = [
    'bg-gradient-to-br from-blue-500/30 to-cyan-500/30 border-blue-400/40',
    'bg-gradient-to-br from-purple-500/30 to-pink-500/30 border-purple-400/40',
    'bg-gradient-to-br from-green-500/30 to-emerald-500/30 border-green-400/40',
    'bg-gradient-to-br from-orange-500/30 to-amber-500/30 border-orange-400/40',
    'bg-gradient-to-br from-rose-500/30 to-red-500/30 border-rose-400/40',
    'bg-gradient-to-br from-indigo-500/30 to-violet-500/30 border-indigo-400/40',
    'bg-gradient-to-br from-teal-500/30 to-cyan-500/30 border-teal-400/40',
    'bg-gradient-to-br from-fuchsia-500/30 to-purple-500/30 border-fuchsia-400/40',
    'bg-gradient-to-br from-lime-500/30 to-green-500/30 border-lime-400/40',
    'bg-gradient-to-br from-amber-500/30 to-yellow-500/30 border-amber-400/40',
    'bg-gradient-to-br from-sky-500/30 to-blue-500/30 border-sky-400/40',
    'bg-gradient-to-br from-pink-500/30 to-rose-500/30 border-pink-400/40',
  ];

  const index = hash % colorPalettes.length;
  return colorPalettes[index];
};

/**
 * 알림 또는 메시지 유형에 따른 색상 클래스를 반환합니다.
 * @param {string} type - 유형 (emergency, voice, medication, schedule 등)
 * @returns {string} Tailwind CSS 클래스 문자열
 */
export const getTypeColor = (type) => {
  if (!type) return 'bg-orange-500';
  const t = type.toLowerCase();

  const typeMap = {
    emergency: 'bg-red-500',
    voice: 'bg-indigo-500',
    medication: 'bg-rose-500',
    schedule: 'bg-amber-500',
  };

  return typeMap[t] || 'bg-orange-500';
};
