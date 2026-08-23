"use client";

import { FormEvent, useMemo, useRef, useState } from "react";
import { api, streamApi } from "../lib/auth";
import Icon from "./Icon";
import LanguageSelector from "./LanguageSelector";
import type { AiLanguage } from "../lib/aiLanguage";
import styles from "./assistantChat.module.css";

type ChatMessage = { role: "user" | "assistant"; content: string };
export type AssistantZone = { id: number; name: string };
type SpeechRecognitionResultEvent = Event & { results: ArrayLike<ArrayLike<{ transcript: string }>> };
type SpeechRecognitionLike = { lang: string; interimResults: boolean; continuous: boolean; onresult: ((event: SpeechRecognitionResultEvent) => void) | null; onend: (() => void) | null; onerror: (() => void) | null; start: () => void; stop: () => void };
type SpeechRecognitionConstructor = new () => SpeechRecognitionLike;

const intro = "Ask me about current campus conditions, alerts, or safety recommendations.";
const MAX_HISTORY_CONTENT_LENGTH = 800;
const voiceLocale: Record<AiLanguage, string> = { en: "en-IN", hi: "hi-IN", or: "or-IN" };

function historyContent(value: string) {
  if (value.length <= MAX_HISTORY_CONTENT_LENGTH) return value;
  return `${value.slice(0, MAX_HISTORY_CONTENT_LENGTH - 3).trimEnd()}...`;
}

function plainText(value: string) {
  return value.replace(/\*\*/g, "").replace(/^\s*#{1,6}\s*/gm, "").replace(/^\s*[-*]\s+/gm, "").trim();
}

function isCompleteResponse(value: string) {
  const text = value.trim();
  return text.length > 0 && /[.!?؟।]$/.test(text);
}

export default function AssistantChatWidget({ zones, language, onLanguageChange }: { zones: AssistantZone[]; language: AiLanguage; onLanguageChange: (language: AiLanguage) => void }) {
  const [open, setOpen] = useState(false);
  const [summaryOpen, setSummaryOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [loading, setLoading] = useState(false);
  const [listening, setListening] = useState(false);
  const [speaking, setSpeaking] = useState(false);
  const [voiceError, setVoiceError] = useState("");
  const recognition = useRef<SpeechRecognitionLike | undefined>(undefined);
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
      let streamed = "";
      let streamCompleted = false;
      const requestBody = JSON.stringify({ message, language, ...(zoneId === undefined ? {} : { zoneId }), conversationHistory: history });
      setMessages((current) => [...current, { role: "assistant", content: "" }]);
      await streamApi("/api/assistant/chat/stream", {
        method: "POST",
        body: requestBody,
      }, (event, data) => {
        if (event === "token") {
          streamed += data;
          setMessages((current) => current.map((item, index) => index === current.length - 1 ? { ...item, content: plainText(streamed) } : item));
        } else if (event === "done") {
          streamCompleted = true;
        } else if (event === "error") {
          throw new Error(data || "The assistant is unavailable right now.");
        }
      });
      if (!streamCompleted || !isCompleteResponse(plainText(streamed))) {
        const complete = await api<{ response: string }>("/api/assistant/chat", { method: "POST", body: requestBody });
        const completeText = plainText(complete.response || "");
        if (!isCompleteResponse(completeText)) throw new Error("The assistant returned an incomplete response. Please try again.");
        setMessages((current) => current.map((item, index) => index === current.length - 1 ? { ...item, content: completeText } : item));
      }
    } catch (reason) {
      const content = plainText(reason instanceof Error ? reason.message : "The assistant is unavailable right now.");
      setMessages((current) => current.map((item, index) => index === current.length - 1 ? { ...item, content } : item));
    } finally {
      setLoading(false);
    }
  }

  function send(event: FormEvent) {
    event.preventDefault();
    void sendMessage(draft.trim());
  }

  function toggleListening() {
    if (listening) { recognition.current?.stop(); setListening(false); return; }
    const browserWindow = window as Window & { SpeechRecognition?: SpeechRecognitionConstructor; webkitSpeechRecognition?: SpeechRecognitionConstructor };
    const Recognition = browserWindow.SpeechRecognition || browserWindow.webkitSpeechRecognition;
    if (!Recognition) { setVoiceError("Voice input is not available in this browser."); return; }
    const next = new Recognition();
    next.lang = voiceLocale[language]; next.interimResults = false; next.continuous = false;
    next.onresult = (event) => {
      const transcript = Array.from({ length: event.results.length }, (_, index) => event.results[index][0]?.transcript || "").join(" ").trim();
      if (transcript) { setDraft(transcript); void sendMessage(transcript); }
    };
    next.onend = () => { setListening(false); recognition.current = undefined; };
    next.onerror = () => { setVoiceError("Voice input could not be understood. Please try again."); setListening(false); };
    recognition.current = next; setVoiceError(""); setListening(true); next.start();
  }

  function speak(message: string) {
    if (typeof window === "undefined" || !window.speechSynthesis) return;
    if (speaking && window.speechSynthesis.speaking) {
      window.speechSynthesis.cancel();
      setSpeaking(false);
      return;
    }
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(message);
    utterance.lang = voiceLocale[language]; utterance.rate = .95;
    utterance.onend = () => setSpeaking(false);
    utterance.onerror = () => setSpeaking(false);
    setSpeaking(true);
    window.speechSynthesis.speak(utterance);
  }

  return <>
    {open && <section className={styles.panel} aria-label="Nirikshan Assistant">
      <header className={styles.panelHeader}><div><strong>Nirikshan Assistant</strong><span>Campus safety only</span></div><div className={styles.headerControls}><LanguageSelector language={language} onChange={onLanguageChange} className={styles.languageSelector} /><button type="button" onClick={() => setOpen(false)} aria-label="Close assistant"><Icon name="close" /></button></div></header>
      <div className={styles.messages} aria-live="polite">{visibleMessages.map((item, index) => <div className={`${styles.message} ${item.role === "user" ? styles.userMessage : styles.assistantMessage}`} key={`${item.role}-${index}`}><span>{plainText(item.content)}</span>{item.role === "assistant" && <button type="button" className={styles.speakButton} onClick={() => speak(plainText(item.content))} aria-label={speaking ? "Stop reading AI response" : "Read AI response aloud"} title={speaking ? "Stop reading" : "Read AI response aloud"}><Icon name={speaking ? "close" : "volume"} /></button>}</div>)}{loading && <div className={`${styles.message} ${styles.assistantMessage} ${styles.loading}`} aria-label="Assistant is responding"><i /><i /><i /></div>}</div>
      <div className={styles.quickActions}>
        <button type="button" onClick={() => setSummaryOpen((current) => !current)} disabled={loading}>Summary</button>
        <button type="button" onClick={() => void sendMessage("What active safety alerts should I know about?")} disabled={loading}>Active alerts</button>
        <button type="button" onClick={() => void sendMessage("Are there any safer route recommendations right now?")} disabled={loading}>Safer routes</button>
      </div>
      {summaryOpen && <div className={styles.summaryMenu} aria-label="Choose summary scope"><strong>Choose summary scope</strong><button type="button" onClick={() => void sendMessage("Give me a concise general summary of current campus safety conditions.")}>Campus-wide summary</button>{zones.map((zone) => <button type="button" key={zone.id} onClick={() => void sendMessage(`Give me a concise current safety summary for ${zone.name}.`, zone.id)}>{zone.name}</button>)}</div>}
      {voiceError && <p className={styles.voiceError} role="alert">{voiceError}</p>}
      <form className={styles.composer} onSubmit={send}><input value={draft} onChange={(event) => setDraft(event.target.value)} maxLength={1200} placeholder="Ask about campus safety..." aria-label="Message Nirikshan Assistant" /><button type="button" className={listening ? styles.listening : ""} onClick={toggleListening} disabled={loading} aria-label={listening ? "Stop voice input" : "Start voice input"} title={listening ? "Stop voice input" : `Speak in ${voiceLocale[language]}`}><Icon name="mic" /></button><button type="submit" disabled={loading || !draft.trim()} aria-label="Send message"><Icon name="arrow" /></button></form>
    </section>}
    <button className={`${styles.launcher} ${open ? styles.launcherOpen : ""}`} type="button" onClick={() => setOpen((current) => !current)} aria-label={open ? "Close Nirikshan Assistant" : "Open Nirikshan Assistant"} aria-expanded={open}><Icon name={open ? "close" : "chat"} /><span>Nirikshan Assistant</span></button>
  </>;
}
