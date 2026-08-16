"use client";

import { ChangeEvent, ReactNode, useEffect, useState } from "react";
import { clearSession, type Role, type UserInfo } from "../lib/auth";
import Icon, { IconName } from "./Icon";
import ThemeToggle from "./ThemeToggle";
import AssistantChatWidget, { type AssistantZone } from "./AssistantChatWidget";
import LanguageSelector from "./LanguageSelector";
import { AI_LANGUAGE_STORAGE_KEY, type AiLanguage } from "../lib/aiLanguage";
import { AiLanguageProvider } from "../lib/aiLanguageContext";
import styles from "./shell.module.css";

export type NavItem = { label: string; href: string; icon: IconName; count?: number; exact?: boolean };
export type PreviewRole = "SECURITY" | "CITIZEN";

export const primaryNavItems: NavItem[] = [
  { label: "Dashboard", href: "/console", icon: "grid" },
  { label: "Administration", href: "/console/admin", icon: "users" },
  { label: "Citizen reports", href: "/console/reports", icon: "activity" },
  { label: "3D Model", href: "/console/3d-map", icon: "map" },
  { label: "Video Ingestion", href: "/admin", icon: "upload" },
  { label: "Simulator", href: "/admin/scenarios", icon: "activity" },
  { label: "Security", href: "/alerts/security", icon: "lock" },
];

const roleLabels: Record<Role, string> = { ADMIN: "Administrator", SECURITY: "Security operator", CITIZEN: "Campus member" };
const switchLabels: Record<Role, string> = { ADMIN: "Administrator", SECURITY: "Security personnel", CITIZEN: "Customer view" };
const sidebarKey = "nirikshan.sidebar-collapsed";

export function AppShell({ user, title, subtitle, active, navItems, previewRole, assistantZones, children }: { user: UserInfo; title: string; subtitle?: string; active: string; navItems: NavItem[]; previewRole?: PreviewRole; assistantZones?: AssistantZone[]; children: ReactNode }) {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [language, setLanguage] = useState<AiLanguage>("en");
  const [queryPreview, setQueryPreview] = useState<PreviewRole | undefined>(() => {
    if (typeof window === "undefined") return undefined;
    const preview = new URLSearchParams(window.location.search).get("preview");
    return preview === "security" ? "SECURITY" : preview === "customer" ? "CITIZEN" : undefined;
  });
  const workspaceRole = previewRole || queryPreview || user.role;
  const homeHref = workspaceRole === "ADMIN" ? "/console" : workspaceRole === "SECURITY" ? "/security" : "/alerts";

  useEffect(() => {
    setCollapsed(window.localStorage.getItem(sidebarKey) === "true");
    const savedLanguage = window.localStorage.getItem(AI_LANGUAGE_STORAGE_KEY);
    if (savedLanguage === "en" || savedLanguage === "hi" || savedLanguage === "or") setLanguage(savedLanguage);
    const preview = new URLSearchParams(window.location.search).get("preview");
    if (preview === "security") setQueryPreview("SECURITY");
    if (preview === "customer") setQueryPreview("CITIZEN");
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

  function changeWorkspace(event: ChangeEvent<HTMLSelectElement>) {
    const destinations: Record<Role, string> = { ADMIN: "/console", SECURITY: "/security?preview=security", CITIZEN: "/alerts?preview=customer" };
    window.location.assign(destinations[event.target.value as Role]);
  }

  function previewHref(href: string) {
    const preview = previewRole || queryPreview;
    if (!preview || href.includes("preview=")) return href;
    const [path, hash] = href.split("#");
    const previewValue = preview === "SECURITY" ? "security" : "customer";
    return `${path}${path.includes("?") ? "&" : "?"}preview=${previewValue}${hash ? `#${hash}` : ""}`;
  }

  const sidebarClass = `${styles.sidebar} ${collapsed ? styles.collapsed : ""} ${mobileOpen ? styles.mobileOpen : ""}`;
  const mainClass = `${styles.main} ${collapsed ? styles.mainCollapsed : ""}`;
  const decoratedHomeHref = previewHref(homeHref);

  return <AiLanguageProvider value={{ language, setLanguage: changeLanguage }}><div className={`${styles.app} ${workspaceRole === "CITIZEN" ? styles.citizenApp : ""}`}>
    <aside className={sidebarClass} aria-label="Application sidebar">
      <div className={styles.sidebarTop}>
        <div className={styles.brandRow}>
          <a className={styles.brand} href={decoratedHomeHref} onClick={() => setMobileOpen(false)}><span className={styles.brandMark}>N</span><span><b>Nirikshan</b><small>Safety intelligence</small></span></a>
          <button className={styles.sidebarToggle} type="button" onClick={toggleCollapsed} aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"} aria-expanded={!collapsed} title={collapsed ? "Expand sidebar" : "Collapse sidebar"}><Icon name="menu" /></button>
        </div>
        {user.role !== "CITIZEN" && <div className={styles.workspaceSwitch}><span className={styles.workspaceDot} />{user.role === "ADMIN" ? <><label className={styles.srOnly} htmlFor="workspace-view">Switch workspace view</label><select id="workspace-view" className={styles.workspaceSelect} value={workspaceRole} onChange={changeWorkspace}><option value="ADMIN">{switchLabels.ADMIN}</option><option value="SECURITY">{switchLabels.SECURITY}</option><option value="CITIZEN">{switchLabels.CITIZEN}</option></select><Icon name="chevron" /></> : <><span>{roleLabels[user.role]}</span><Icon name="chevron" /></>}</div>}
      </div>
      <nav className={styles.sideNav} aria-label="Primary navigation">{navItems.map((item) => <a key={item.href} className={`${styles.navItem} ${active === item.label ? styles.active : ""}`} href={previewHref(item.href)} aria-current={active === item.label ? "page" : undefined} onClick={() => setMobileOpen(false)}><Icon name={item.icon} /><span>{item.label}</span>{item.count !== undefined && <b>{item.count}</b>}</a>)}</nav>
      <div className={styles.sidebarBottom}>
        <a className={styles.accountLink} href={previewHref("/alerts/security")} onClick={() => setMobileOpen(false)}><span className={styles.avatar}>{user.name.slice(0, 1).toUpperCase()}</span><span><strong>{user.name}</strong><small>{user.email}</small></span></a>
        <button className={styles.signOut} type="button" onClick={signOut}><Icon name="logout" /><span>Sign out</span></button>
      </div>
    </aside>
    {mobileOpen && <button className={styles.mobileBackdrop} type="button" aria-label="Close navigation" onClick={() => setMobileOpen(false)} />}
    <header className={styles.mobileHeader}><a className={styles.brand} href={decoratedHomeHref}><span className={styles.brandMark}>N</span><b>Nirikshan</b></a><LanguageSelector language={language} onChange={changeLanguage} className={styles.languageSelector} /><ThemeToggle /><button className={styles.mobileMenu} type="button" onClick={() => setMobileOpen((current) => !current)} aria-label={mobileOpen ? "Close navigation" : "Open navigation"} aria-expanded={mobileOpen}><Icon name="menu" /></button></header>
    <main className={mainClass}>
      <div className={styles.mobileNav}>{navItems.map((item) => <a key={item.href} className={active === item.label ? styles.mobileActive : ""} href={previewHref(item.href)}><Icon name={item.icon} /><span>{item.label}</span></a>)}</div>
      <header className={styles.pageHeader}><div><div className={styles.breadcrumb}>Nirikshan <span>/</span> {title}</div><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div><div className={styles.headerAction}><LanguageSelector language={language} onChange={changeLanguage} className={styles.languageSelector} /><ThemeToggle /></div></header>
      {queryPreview && <div className={styles.previewBanner} role="status">{queryPreview === "SECURITY" ? "Security personnel preview is active." : "Customer view preview is active."} You are still signed in with administrator permissions.</div>}
      {children}
      <AssistantChatWidget language={language} onLanguageChange={changeLanguage} zones={assistantZones ?? (user.assignedZoneId && user.assignedZoneName ? [{ id: user.assignedZoneId, name: user.assignedZoneName }] : [])} />
    </main>
  </div></AiLanguageProvider>;
}

export default AppShell;
