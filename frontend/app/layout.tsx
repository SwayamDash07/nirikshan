import type { Metadata, Viewport } from "next";
import "./globals.css";
import "leaflet/dist/leaflet.css";
import { ThemeProvider } from "./components/ThemeProvider";

export const metadata: Metadata = {
  title: "Nirikshan | Crowd Safety Intelligence",
  description: "AI-powered crowd safety command dashboard",
  manifest: "/manifest.json",
};

export const viewport: Viewport = { themeColor: "#14345f" };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en" suppressHydrationWarning><head><script dangerouslySetInnerHTML={{ __html: `(function(){try{var k="nirikshan.theme",m=localStorage.getItem(k)||"system",t=m==="system"?(matchMedia("(prefers-color-scheme: dark)").matches?"dark":"light"):m;document.documentElement.dataset.theme=t}catch(e){}})()` }} /></head><body><ThemeProvider>{children}</ThemeProvider></body></html>;
}
