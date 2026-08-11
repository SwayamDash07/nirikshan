import type { Metadata, Viewport } from "next";
import "./globals.css";
import "leaflet/dist/leaflet.css";

export const metadata: Metadata = {
  title: "Nirikshan | Crowd Safety Intelligence",
  description: "AI-powered crowd safety command dashboard",
  manifest: "/manifest.json",
};

export const viewport: Viewport = { themeColor: "#14345f" };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>;
}
