import { SVGProps } from "react";

export type IconName = "grid" | "map" | "bell" | "shield" | "upload" | "users" | "settings" | "arrow" | "activity" | "clipboard" | "camera" | "logout" | "menu" | "chevron" | "check" | "lock" | "close" | "chat" | "mic" | "volume";

export default function Icon({ name, ...props }: { name: IconName } & SVGProps<SVGSVGElement>) {
  if (name === "settings") {
    return <svg viewBox="0 0 24 24" fill="none" {...props}><image href="/setting.png" x="1" y="1" width="22" height="22" preserveAspectRatio="xMidYMid meet" style={{ filter: "invert(1)" }} /></svg>;
  }
  const paths = {
    grid: <><rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" /><rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" /></>,
    map: <><path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3Z" /><path d="M9 3v15M15 6v15" /></>,
    bell: <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9Z" /><path d="M10 21h4" /></>,
    shield: <><path d="M12 3 20 6v5c0 5.2-3.3 8.7-8 10-4.7-1.3-8-4.8-8-10V6l8-3Z" /><path d="m8.5 12 2.2 2.2 4.8-5" /></>,
    upload: <><path d="M12 16V4" /><path d="m7 9 5-5 5 5" /><path d="M4 15v4h16v-4" /></>,
    users: <><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" /></>,
    arrow: <><path d="M5 12h14" /><path d="m13 6 6 6-6 6" /></>,
    activity: <><path d="M3 12h4l2-7 4 14 2-7h6" /></>,
    clipboard: <><rect x="5" y="4" width="14" height="17" rx="2" /><path d="M9 4.5V3h6v1.5M8 10h8M8 14h6M8 18h4" /></>,
    camera: <><path d="M4 7h3l1.5-2h7L17 7h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1Z" /><circle cx="12" cy="13" r="3.5" /></>,
    logout: <><path d="M10 17l5-5-5-5" /><path d="M15 12H3" /><path d="M21 3v18" /></>,
    menu: <><path d="M4 6h16M4 12h16M4 18h16" /></>,
    chevron: <path d="m8 10 4 4 4-4" />,
    check: <path d="m5 12 4 4L19 6" />,
    lock: <><rect x="4" y="10" width="16" height="11" rx="2" /><path d="M8 10V7a4 4 0 0 1 8 0v3" /></>,
    close: <><path d="m6 6 12 12" /><path d="m18 6-12 12" /></>,
    chat: <><path d="M5 5h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H11l-4 3v-3H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z" /><path d="M7 9h10M7 13h6" /></>,
    mic: <><path d="M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3Z" /><path d="M19 11a7 7 0 0 1-14 0M12 18v3M9 21h6" /></>,
    volume: <><path d="M4 10v4h4l5 4V6l-5 4H4Z" /><path d="M17 9a4 4 0 0 1 0 6M19 6a8 8 0 0 1 0 12" /></>,
  }[name];
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" {...props}>{paths}</svg>;
}
