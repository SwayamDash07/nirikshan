"use client";

import { ReactNode, useEffect, useRef, useState } from "react";
import { api, clearSession, type Role, type UserInfo } from "../lib/auth";
import Icon, { IconName } from "./Icon";
import ThemeToggle from "./ThemeToggle";
import AssistantChatWidget, { type AssistantZone } from "./AssistantChatWidget";
import LanguageSelector from "./LanguageSelector";
import { AI_LANGUAGE_STORAGE_KEY, type AiLanguage } from "../lib/aiLanguage";
import { AiLanguageProvider } from "../lib/aiLanguageContext";
import { usePageLanguage } from "../lib/pageLanguage";
import { pendingOfflineReports, syncOfflineReports } from "../lib/offlineSync";
import styles from "./shell.module.css";

export type NavItem = { label: string; href: string; icon: IconName; count?: number; exact?: boolean };
export type PreviewRole = "SECURITY" | "CITIZEN";

const localCvEnabled = process.env.NEXT_PUBLIC_LOCAL_CV_ENABLED === "true";
export const primaryNavItems: NavItem[] = [
  { label: "Dashboard", href: "/console", icon: "grid" },
  { label: "Video Sessions", href: "/console/video-sessions", icon: "activity" },
  { label: "Administration", href: "/console/admin", icon: "users" },
  { label: "Citizen reports", href: "/console/reports", icon: "activity" },
  { label: "3D Model", href: "/console/3d-map", icon: "map" },
  ...(localCvEnabled ? [{ label: "Video Ingestion", href: "/admin", icon: "upload" as IconName }, { label: "Simulator", href: "/admin/scenarios", icon: "activity" as IconName }] : []),
  { label: "Security", href: "/alerts/security", icon: "lock" },
];

const roleLabels: Record<Role, string> = { ADMIN: "Administrator", SECURITY: "Security operator", CITIZEN: "Campus member" };
const switchLabels: Record<Role, string> = { ADMIN: "Administrator", SECURITY: "Security personnel", CITIZEN: "Citizen view" };
const sidebarKey = "nirikshan.sidebar-collapsed";

export function AppShell({ user, title, subtitle, active, navItems, previewRole, assistantZones, children }: { user: UserInfo; title: string; subtitle?: string; active: string; navItems: NavItem[]; previewRole?: PreviewRole; assistantZones?: AssistantZone[]; children: ReactNode }) {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [offline, setOffline] = useState(false);
  const [pendingReports, setPendingReports] = useState(0);
  const [language, setLanguage] = useState<AiLanguage>("en");
  const [roleMenuOpen, setRoleMenuOpen] = useState(false);
  const roleMenuRef = useRef<HTMLDivElement>(null);
  const [queryPreview, setQueryPreview] = useState<PreviewRole | undefined>(() => {
    if (typeof window === "undefined") return undefined;
    const preview = new URLSearchParams(window.location.search).get("preview");
    return preview === "security" ? "SECURITY" : preview === "citizen" || preview === "customer" ? "CITIZEN" : undefined;
  });
  const [previewBannerVisible, setPreviewBannerVisible] = useState(false);
  const workspaceRole = previewRole || queryPreview || user.role;
  const homeHref = workspaceRole === "ADMIN" ? "/console" : workspaceRole === "SECURITY" ? "/security" : "/alerts";
  usePageLanguage(language);

  useEffect(() => {
    setCollapsed(window.localStorage.getItem(sidebarKey) === "true");
    const savedLanguage = window.localStorage.getItem(AI_LANGUAGE_STORAGE_KEY);
    if (savedLanguage === "en" || savedLanguage === "hi" || savedLanguage === "or") setLanguage(savedLanguage);
    const preview = new URLSearchParams(window.location.search).get("preview");
    if (preview === "security") setQueryPreview("SECURITY");
    if (preview === "citizen" || preview === "customer") setQueryPreview("CITIZEN");
  }, []);

  useEffect(() => {
    if (!queryPreview) {
      setPreviewBannerVisible(false);
      return;
    }
    const previewKey = `nirikshan.preview-banner.${queryPreview.toLowerCase()}`;
    if (window.sessionStorage.getItem(previewKey) === "shown") {
      setPreviewBannerVisible(false);
      return;
    }
    window.sessionStorage.setItem(previewKey, "shown");
    setPreviewBannerVisible(true);
    const timer = window.setTimeout(() => setPreviewBannerVisible(false), 10000);
    return () => window.clearTimeout(timer);
  }, [queryPreview]);

  useEffect(() => {
    if (!roleMenuOpen) return;
    const closeOnOutsideClick = (event: MouseEvent) => {
      if (!roleMenuRef.current?.contains(event.target as Node)) setRoleMenuOpen(false);
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setRoleMenuOpen(false);
    };
    document.addEventListener("mousedown", closeOnOutsideClick);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("mousedown", closeOnOutsideClick);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [roleMenuOpen]);

  useEffect(() => {
    const refresh = () => pendingOfflineReports().then((items) => setPendingReports(items.length)).catch(() => undefined);
    const synchronize = () => syncOfflineReports((report) => api("/api/citizen-reports", { method: "POST", body: JSON.stringify({ zoneId: report.zoneId, description: report.description, clientEventId: report.clientEventId }) })).then((items) => setPendingReports(items.length)).catch(() => undefined);
    const online = () => { setOffline(false); void synchronize(); };
    const offlineState = () => setOffline(true);
    setOffline(!navigator.onLine); refresh();
    if (navigator.onLine) void synchronize();
    window.addEventListener("online", online); window.addEventListener("offline", offlineState);
    return () => { window.removeEventListener("online", online); window.removeEventListener("offline", offlineState); };
  }, []);

  function changeLanguage(next: AiLanguage) {
    setLanguage(next);
    window.localStorage.setItem(AI_LANGUAGE_STORAGE_KEY, next);
  }

  function toggleCollapsed() {
    setCollapsed((current) => {
      const next = !current;
      window.localStorage.setItem(sidebarKey, String(next));
      return next;
    });
  }

  function signOut() {
    clearSession();
    window.location.replace("/alerts");
  }

  function changeWorkspace(nextRole: Role) {
    const destinations: Record<Role, string> = { ADMIN: "/console", SECURITY: "/security?preview=security", CITIZEN: "/alerts?preview=citizen" };
    if (nextRole === "CITIZEN") {
      window.sessionStorage.removeItem("nirikshan.preview-banner.citizen");
      window.sessionStorage.removeItem("nirikshan.preview-citizen.location-shown");
      window.sessionStorage.removeItem("nirikshan.preview-customer.location-shown");
    }
    if (nextRole === "SECURITY") window.sessionStorage.removeItem("nirikshan.preview-banner.security");
    setRoleMenuOpen(false);
    window.location.assign(destinations[nextRole]);
  }

  function previewHref(href: string) {
    const preview = previewRole || queryPreview;
    if (!preview || href.includes("preview=")) return href;
    const [path, hash] = href.split("#");
    const previewValue = preview === "SECURITY" ? "security" : "citizen";
    return `${path}${path.includes("?") ? "&" : "?"}preview=${previewValue}${hash ? `#${hash}` : ""}`;
  }

  const sidebarClass = `${styles.sidebar} ${collapsed ? styles.collapsed : ""} ${mobileOpen ? styles.mobileOpen : ""}`;
  const mainClass = `${styles.main} ${collapsed ? styles.mainCollapsed : ""}`;
  const decoratedHomeHref = previewHref(homeHref);

  return <AiLanguageProvider value={{ language, setLanguage: changeLanguage }}><div className={`${styles.app} ${workspaceRole === "CITIZEN" ? styles.citizenApp : ""}`}>
    <aside className={sidebarClass} aria-label="Application sidebar">
      <div className={styles.sidebarTop}>
        <div className={styles.brandRow}>
          <a className={styles.brand} href={decoratedHomeHref} onClick={() => setMobileOpen(false)}><span><b>Nirikshan</b><small>Safety intelligence</small></span></a>
          <button className={styles.sidebarToggle} type="button" onClick={toggleCollapsed} aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"} aria-expanded={!collapsed} title={collapsed ? "Expand sidebar" : "Collapse sidebar"}><Icon name="menu" /></button>
        </div>
        {user.role !== "CITIZEN" && <div ref={roleMenuRef} className={`${styles.workspaceSwitch} ${roleMenuOpen ? styles.workspaceOpen : ""}`}>
          {user.role === "ADMIN" ? <>
            <button type="button" className={styles.workspaceTrigger} aria-haspopup="listbox" aria-expanded={roleMenuOpen} onClick={() => setRoleMenuOpen((current) => !current)}>
              <span>{switchLabels[workspaceRole]}</span><Icon name="chevron" />
            </button>
            {roleMenuOpen && <div className={styles.workspaceMenu} role="listbox" aria-label="Workspace view">
              {(["ADMIN", "SECURITY", "CITIZEN"] as Role[]).map((role) => <button key={role} type="button" className={`${styles.workspaceOption} ${workspaceRole === role ? styles.workspaceOptionSelected : ""}`} role="option" aria-selected={workspaceRole === role} onClick={() => changeWorkspace(role)}>{switchLabels[role]}</button>)}
            </div>}
          </> : <><span>{roleLabels[user.role]}</span><Icon name="chevron" /></>}
        </div>}
      </div>
      <nav className={styles.sideNav} aria-label="Primary navigation">{navItems.map((item) => <a key={item.href} className={`${styles.navItem} ${active === item.label ? styles.active : ""}`} href={previewHref(item.href)} aria-current={active === item.label ? "page" : undefined} onClick={() => setMobileOpen(false)}><Icon name={item.icon} /><span>{item.label}</span>{item.count !== undefined && <b>{item.count}</b>}</a>)}</nav>
      <div className={styles.sidebarBottom}>
        <a className={styles.accountLink} href={previewHref("/alerts/security")} onClick={() => setMobileOpen(false)}><span className={styles.avatar}>{user.name.slice(0, 1).toUpperCase()}</span><span><strong>{user.name}</strong><small>{user.email}</small></span></a>
        <button className={styles.signOut} type="button" onClick={signOut}><Icon name="logout" /><span>Sign out</span></button>
      </div>
    </aside>
    {mobileOpen && <button className={styles.mobileBackdrop} type="button" aria-label="Close navigation" onClick={() => setMobileOpen(false)} />}
    <header className={styles.mobileHeader}><a className={styles.brand} href={decoratedHomeHref}><b>Nirikshan</b></a><LanguageSelector language={language} onChange={changeLanguage} className={styles.languageSelector} /><ThemeToggle /><button className={styles.mobileMenu} type="button" onClick={() => setMobileOpen((current) => !current)} aria-label={mobileOpen ? "Close navigation" : "Open navigation"} aria-expanded={mobileOpen}><Icon name="menu" /></button></header>
    <main className={mainClass}>
      <div className={styles.mobileNav}>{navItems.map((item) => <a key={item.href} className={active === item.label ? styles.mobileActive : ""} href={previewHref(item.href)}><Icon name={item.icon} /><span>{item.label}</span></a>)}</div>
      <header className={styles.pageHeader}><div><div className={styles.breadcrumb}>Nirikshan <span>/</span> {title}</div><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div><div className={styles.headerAction}><LanguageSelector language={language} onChange={changeLanguage} className={styles.languageSelector} /><ThemeToggle /></div></header>
      {queryPreview && previewBannerVisible && <div className={styles.previewBanner} role="status"><span>{queryPreview === "SECURITY" ? "Security personnel preview is active." : "Citizen view preview is active."} You are still signed in with administrator permissions.</span><small className={styles.previewDuration}>10s</small><i className={styles.previewProgress} aria-hidden="true" /></div>}
      {(offline || pendingReports > 0) && <div className={styles.offlineBanner} role="status">{offline ? "Offline mode: showing the last safe data available." : "Connection restored."}{pendingReports > 0 && ` ${pendingReports} incident report${pendingReports === 1 ? "" : "s"} waiting to sync.`}</div>}
      {children}
      <AssistantChatWidget language={language} onLanguageChange={changeLanguage} zones={assistantZones ?? (user.assignedZoneId && user.assignedZoneName ? [{ id: user.assignedZoneId, name: user.assignedZoneName }] : [])} />
    </main>
  </div></AiLanguageProvider>;
}

export default AppShell;
