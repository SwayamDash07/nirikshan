import { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";
import styles from "./ui.module.css";

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "secondary" | "ghost" | "danger"; size?: "sm" | "md" | "lg" };

export function Button({ variant = "primary", size = "md", className = "", ...props }: ButtonProps) {
  return <button className={`${styles.button} ${styles[variant]} ${styles[size]} ${className}`} {...props} />;
}

export function Field({ label, hint, error, children }: { label: string; hint?: string; error?: string; children: ReactNode }) {
  return <label className={styles.field}><span className={styles.label}>{label}</span>{children}{error ? <span className={styles.errorText}>{error}</span> : hint ? <span className={styles.hint}>{hint}</span> : null}</label>;
}

export function Input(props: InputHTMLAttributes<HTMLInputElement>) { return <input className={styles.input} {...props} />; }
export function Select(props: SelectHTMLAttributes<HTMLSelectElement>) { return <select className={styles.input} {...props} />; }
export function Textarea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) { return <textarea className={`${styles.input} ${styles.textarea}`} {...props} />; }

export function Card({ children, className = "", ...props }: { children: ReactNode; className?: string } & React.HTMLAttributes<HTMLElement>) {
  return <section className={`${styles.card} ${className}`} {...props}>{children}</section>;
}

export function Spinner({ label = "Loading" }: { label?: string }) {
  return <div className={styles.loading} role="status"><span className={styles.spinner} />{label}</div>;
}
