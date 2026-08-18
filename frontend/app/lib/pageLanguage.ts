"use client";

import { useEffect } from "react";
import type { AiLanguage } from "./aiLanguage";

const translations: Record<Exclude<AiLanguage, "en">, Record<string, string>> = {
  hi: {
    "Dashboard": "डैशबोर्ड", "Administration": "प्रशासन", "Citizen reports": "नागरिक रिपोर्ट", "Video Ingestion": "वीडियो इनपुट", "Simulator": "सिम्युलेटर", "Security": "सुरक्षा", "Sign out": "साइन आउट", "Refresh data": "डेटा रीफ़्रेश करें", "Refreshing": "रीफ़्रेश हो रहा है", "Your safety briefing": "आपकी सुरक्षा जानकारी", "Campus map": "कैंपस मानचित्र", "Nearby areas and current conditions.": "पास के क्षेत्र और वर्तमान स्थिति।", "Location on": "लोकेशन चालू", "Location off": "लोकेशन बंद", "Live briefing": "लाइव जानकारी", "All clear for now": "अभी सब सुरक्षित है", "Safer routes": "सुरक्षित मार्ग", "Safety alerts": "सुरक्षा अलर्ट", "Updates near you": "आपके पास के अपडेट", "No active alerts.": "कोई सक्रिय अलर्ट नहीं है।", "Follow these staff-approved instructions": "कर्मचारियों द्वारा स्वीकृत निर्देशों का पालन करें", "Early safety update": "प्रारंभिक सुरक्षा अपडेट", "Campus density trend": "कैंपस भीड़ प्रवृत्ति", "Route recommendations": "मार्ग सुझाव", "Review safety alerts": "सुरक्षा अलर्ट देखें", "SIMULATION MODE": "सिमुलेशन मोड", "HIGH": "उच्च", "MEDIUM": "मध्यम", "LOW": "कम", "CRITICAL": "अत्यंत गंभीर", "Start voice input": "आवाज़ से बोलना शुरू करें", "Stop voice input": "आवाज़ इनपुट रोकें", "Read AI response aloud": "AI उत्तर सुनें", "Ask about campus safety...": "कैंपस सुरक्षा के बारे में पूछें...", "Summary": "सारांश", "Active alerts": "सक्रिय अलर्ट", "Campus-wide summary": "पूरे कैंपस का सारांश", "Send": "भेजें", "Report an issue": "समस्या रिपोर्ट करें", "Try again": "फिर कोशिश करें", "Ask me about current campus conditions, alerts, or safety recommendations.": "कैंपस की वर्तमान स्थिति, अलर्ट या सुरक्षा सुझावों के बारे में पूछें।"
  },
  or: {
    "Dashboard": "ଡ୍ୟାସବୋର୍ଡ", "Administration": "ପ୍ରଶାସନ", "Citizen reports": "ନାଗରିକ ରିପୋର୍ଟ", "Video Ingestion": "ଭିଡିଓ ଇନପୁଟ୍", "Simulator": "ସିମୁଲେଟର", "Security": "ସୁରକ୍ଷା", "Sign out": "ସାଇନ୍ ଆଉଟ୍", "Refresh data": "ଡାଟା ରିଫ୍ରେସ୍ କରନ୍ତୁ", "Refreshing": "ରିଫ୍ରେସ୍ ହେଉଛି", "Your safety briefing": "ଆପଣଙ୍କ ସୁରକ୍ଷା ସୂଚନା", "Campus map": "କ୍ୟାମ୍ପସ୍ ମାନଚିତ୍ର", "Nearby areas and current conditions.": "ନିକଟସ୍ଥ ଅଞ୍ଚଳ ଏବଂ ବର୍ତ୍ତମାନ ସ୍ଥିତି।", "Location on": "ଲୋକେସନ୍ ଚାଲୁ", "Location off": "ଲୋକେସନ୍ ବନ୍ଦ", "Live briefing": "ଲାଇଭ୍ ସୂଚନା", "All clear for now": "ବର୍ତ୍ତମାନ ସବୁ ସୁରକ୍ଷିତ", "Safer routes": "ସୁରକ୍ଷିତ ରାସ୍ତା", "Safety alerts": "ସୁରକ୍ଷା ଆଲର୍ଟ", "Updates near you": "ଆପଣଙ୍କ ନିକଟର ଅପଡେଟ୍", "No active alerts.": "କୌଣସି ସକ୍ରିୟ ଆଲର୍ଟ ନାହିଁ।", "Follow these staff-approved instructions": "କର୍ମଚାରୀଙ୍କ ଅନୁମୋଦିତ ନିର୍ଦ୍ଦେଶ ପାଳନ କରନ୍ତୁ", "Early safety update": "ପ୍ରାରମ୍ଭିକ ସୁରକ୍ଷା ଅପଡେଟ୍", "Campus density trend": "କ୍ୟାମ୍ପସ୍ ଭିଡ଼ ପ୍ରବଣତା", "Route recommendations": "ରାସ୍ତା ସୁପାରିଶ", "Review safety alerts": "ସୁରକ୍ଷା ଆଲର୍ଟ ଦେଖନ୍ତୁ", "SIMULATION MODE": "ସିମୁଲେସନ୍ ମୋଡ୍", "HIGH": "ଅଧିକ", "MEDIUM": "ମଧ୍ୟମ", "LOW": "କମ୍", "CRITICAL": "ଅତ୍ୟନ୍ତ ଗୁରୁତର", "Start voice input": "ଭଏସ୍ ଇନପୁଟ୍ ଆରମ୍ଭ କରନ୍ତୁ", "Stop voice input": "ଭଏସ୍ ଇନପୁଟ୍ ବନ୍ଦ କରନ୍ତୁ", "Read AI response aloud": "AI ଉତ୍ତର ଶୁଣନ୍ତୁ", "Ask about campus safety...": "କ୍ୟାମ୍ପସ୍ ସୁରକ୍ଷା ବିଷୟରେ ପଚାରନ୍ତୁ...", "Summary": "ସାରାଂଶ", "Active alerts": "ସକ୍ରିୟ ଆଲର୍ଟ", "Campus-wide summary": "ସମଗ୍ର କ୍ୟାମ୍ପସ୍ ସାରାଂଶ", "Send": "ପଠାନ୍ତୁ", "Report an issue": "ସମସ୍ୟା ରିପୋର୍ଟ କରନ୍ତୁ", "Try again": "ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ", "Ask me about current campus conditions, alerts, or safety recommendations.": "କ୍ୟାମ୍ପସ୍‌ର ବର୍ତ୍ତମାନ ସ୍ଥିତି, ଆଲର୍ଟ କିମ୍ବା ସୁରକ୍ଷା ସୁପାରିଶ ବିଷୟରେ ପଚାରନ୍ତୁ।"
  }
};

const originals = new WeakMap<Text, string>();

function translated(value: string, language: AiLanguage) {
  if (language === "en") return value;
  const dictionary = translations[language];
  return dictionary[value] || value;
}

function translate(root: HTMLElement, language: AiLanguage) {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  const nodes: Text[] = [];
  let node: Node | null;
  while ((node = walker.nextNode())) nodes.push(node as Text);
  nodes.forEach((text) => {
    if (text.parentElement?.closest("script,style,textarea")) return;
    const source = originals.get(text) || text.data;
    originals.set(text, source);
    const next = translated(source, language);
    if (text.data !== next) text.data = next;
  });
  root.querySelectorAll<HTMLElement>("input,textarea,button").forEach((element) => {
    ["placeholder", "aria-label", "title"].forEach((attribute) => {
      const value = element.getAttribute(attribute);
      if (value) element.setAttribute(attribute, translated(value, language));
    });
  });
}

export function usePageLanguage(language: AiLanguage) {
  useEffect(() => {
    const root = document.body;
    let applying = false;
    const apply = () => { if (applying) return; applying = true; translate(root, language); applying = false; };
    apply();
    const observer = new MutationObserver(apply);
    observer.observe(root, { childList: true, subtree: true, characterData: true });
    return () => observer.disconnect();
  }, [language]);
}

export function pageText(value: string, language: AiLanguage) { return translated(value, language); }
