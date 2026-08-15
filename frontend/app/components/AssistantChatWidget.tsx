"use client";

import { FormEvent, useMemo, useState } from "react";
import { api } from "../lib/auth";
import Icon from "./Icon";
import styles from "./assistantChat.module.css";

type ChatMessage = { role: "user" | "assistant"; content: string };
type ChatResponse = { response: string };
export type AssistantZone = { id: number; name: string };

const intro = "Ask me about current campus conditions, alerts, or safety recommendations.";
const MAX_HISTORY_CONTENT_LENGTH = 800;

function historyContent(value: string) {
  if (value.length <= MAX_HISTORY_CONTENT_LENGTH) return value;
  return `${value.slice(0, MAX_HISTORY_CONTENT_LENGTH - 3).trimEnd()}...`;
}

function plainText(value: string) {
  return value.replace(/\*\*/g, "").replace(/^\s*#{1,6}\s*/gm, "").replace(/^\s*[-*]\s+/gm, "").trim();
}

export default function AssistantChatWidget({ zones }: { zones: AssistantZone[] }) {
  const [open, setOpen] = useState(false);
  const [summaryOpen, setSummaryOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [loading, setLoading] = useState(false);
  const visibleMessages = useMemo(() => messages.length ? messages : [{ role: "assistant" as const, content: intro }], [messages]);

  async function sendMessage(message: string, zoneId?: number) {
    if (!message.trim() || loading) return;
    const history = messages.slice(-8).map((item) => ({
      role: item.role,
      content: historyContent(item.content),
    }));
    setDraft("");
    setSummaryOpen(false);
    setMessages((current) => [...current, { role: "user", content: message }]);
    setLoading(true);
    try {
      const result = await api<ChatResponse>("/api/assistant/chat", {
        method: "POST",
        body: JSON.stringify({ message, ...(zoneId === undefined ? {} : { zoneId }), conversationHistory: history }),
      });
      setMessages((current) => [...current, { role: "assistant", content: plainText(result.response) }]);
    } catch (reason) {
      setMessages((current) => [...current, { role: "assistant", content: plainText(reason instanceof Error ? reason.message : "The assistant is unavailable right now.") }]);
    } finally {
      setLoading(false);
    }
  }

  function send(event: FormEvent) {
    event.preventDefault();
    void sendMessage(draft.trim());
  }

  return <>
    {open && <section className={styles.panel} aria-label="Nirikshan Assistant">
      <header className={styles.panelHeader}><div><strong>Nirikshan Assistant</strong><span>Campus safety only</span></div><button type="button" onClick={() => setOpen(false)} aria-label="Close assistant"><Icon name="close" /></button></header>
      <div className={styles.messages} aria-live="polite">{visibleMessages.map((item, index) => <div className={`${styles.message} ${item.role === "user" ? styles.userMessage : styles.assistantMessage}`} key={`${item.role}-${index}`}>{plainText(item.content)}</div>)}{loading && <div className={`${styles.message} ${styles.assistantMessage} ${styles.loading}`} aria-label="Assistant is responding"><i /><i /><i /></div>}</div>
      <div className={styles.quickActions}>
        <button type="button" onClick={() => setSummaryOpen((current) => !current)} disabled={loading}>Summary</button>
        <button type="button" onClick={() => void sendMessage("What active safety alerts should I know about?")} disabled={loading}>Active alerts</button>
        <button type="button" onClick={() => void sendMessage("Are there any safer route recommendations right now?")} disabled={loading}>Safer routes</button>
      </div>
      {summaryOpen && <div className={styles.summaryMenu} aria-label="Choose summary scope"><strong>Choose summary scope</strong><button type="button" onClick={() => void sendMessage("Give me a concise general summary of current campus safety conditions.")}>Campus-wide summary</button>{zones.map((zone) => <button type="button" key={zone.id} onClick={() => void sendMessage(`Give me a concise current safety summary for ${zone.name}.`, zone.id)}>{zone.name}</button>)}</div>}
      <form className={styles.composer} onSubmit={send}><input value={draft} onChange={(event) => setDraft(event.target.value)} maxLength={1200} placeholder="Ask about campus safety..." aria-label="Message Nirikshan Assistant" /><button type="submit" disabled={loading || !draft.trim()} aria-label="Send message"><Icon name="arrow" /></button></form>
    </section>}
    <button className={`${styles.launcher} ${open ? styles.launcherOpen : ""}`} type="button" onClick={() => setOpen((current) => !current)} aria-label={open ? "Close Nirikshan Assistant" : "Open Nirikshan Assistant"} aria-expanded={open}><Icon name={open ? "close" : "chat"} /><span>Nirikshan Assistant</span></button>
  </>;
}
