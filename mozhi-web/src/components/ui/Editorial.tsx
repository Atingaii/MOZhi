import clsx from "clsx";
import type { InputHTMLAttributes, PropsWithChildren, ReactNode } from "react";
import { Link } from "react-router-dom";

export interface HeroLink {
  href: string;
  label: string;
  external?: boolean;
}

interface PageHeroProps {
  badge?: string;
  title: string;
  description: string;
  links?: HeroLink[];
  children?: ReactNode;
}

interface SectionHeadingProps {
  title: string;
  subtitle: string;
}

interface FeaturedCardProps {
  meta: string;
  title: string;
  description: string;
  className?: string;
  children?: ReactNode;
}

interface ListRowProps {
  title: string;
  meta: string;
  description?: string;
}

interface InfoCardProps {
  accent: string;
  eyebrow: string;
  title: string;
  description: string;
  className?: string;
}

interface ProjectCardProps {
  title: string;
  description: string;
  tags: string[];
}

interface TimelineRowProps {
  title: string;
  period: string;
  role: string;
  description: string;
}

interface SubscribePanelProps {
  title: string;
  subtitle: string;
  placeholder: string;
  buttonLabel: string;
}

export function PageHero({ badge, title, description, links = [], children }: PageHeroProps) {
  return (
    <section className="mozhi-hero">
      {badge ? (
        <span className="mozhi-hero-badge">
          <span className="mozhi-hero-badge-dot" />
          {badge}
        </span>
      ) : null}
      <h1 className="mozhi-hero-title">{title}</h1>
      <p className="mozhi-hero-description">{description}</p>
      {links.length > 0 ? (
        <div className="mozhi-hero-links">
          {links.map((link) => (
            link.external ? (
              <a
                key={`${link.href}-${link.label}`}
                className="mozhi-link-inline"
                href={link.href}
                rel="noreferrer"
                target="_blank"
              >
                {link.label}
              </a>
            ) : (
              <Link key={`${link.href}-${link.label}`} className="mozhi-link-inline" to={link.href}>
                {link.label}
              </Link>
            )
          ))}
        </div>
      ) : null}
      {children}
    </section>
  );
}

export function PageSection({ children }: PropsWithChildren) {
  return <section className="mozhi-page-section">{children}</section>;
}

export function SectionHeading({ title, subtitle }: SectionHeadingProps) {
  return (
    <div className="mozhi-section-header">
      <h2 className="mozhi-section-title">{title}</h2>
      <p className="mozhi-section-subtitle">{subtitle}</p>
    </div>
  );
}

export function FeaturedCard({ meta, title, description, className, children }: FeaturedCardProps) {
  return (
    <article className={clsx("mozhi-featured-card", className)}>
      <div className="mozhi-featured-media" />
      <div className="mozhi-featured-body">
        <div className="mozhi-featured-meta">{meta}</div>
        <h3 className="mozhi-featured-title">{title}</h3>
        <p className="mozhi-featured-description">{description}</p>
        {children}
      </div>
    </article>
  );
}

export function ListRow({ title, meta, description }: ListRowProps) {
  return (
    <div className="mozhi-list-row">
      <div className="mozhi-list-content">
        <div className="mozhi-list-title">{title}</div>
        {description ? <p className="mozhi-list-description">{description}</p> : null}
      </div>
      <span className="mozhi-list-meta">{meta}</span>
    </div>
  );
}

export function InfoGrid({ children }: PropsWithChildren) {
  return <div className="mozhi-grid-2">{children}</div>;
}

export function InfoCard({ accent, eyebrow, title, description, className }: InfoCardProps) {
  return (
    <article className={clsx("mozhi-info-card", className)}>
      <div className="mozhi-info-icon" style={{ background: accent }}>
        {eyebrow.slice(0, 1)}
      </div>
      <div className="mozhi-info-copy">
        <span className="mozhi-info-eyebrow">{eyebrow}</span>
        <h4 className="mozhi-info-title">{title}</h4>
        <p className="mozhi-info-description">{description}</p>
      </div>
    </article>
  );
}

export function ProjectGrid({ children }: PropsWithChildren) {
  return <div className="mozhi-project-grid">{children}</div>;
}

export function ProjectCard({ title, description, tags }: ProjectCardProps) {
  return (
    <article className="mozhi-project-card">
      <div className="mozhi-project-thumb" />
      <div className="mozhi-project-content">
        <h4 className="mozhi-project-title">{title}</h4>
        <p className="mozhi-project-description">{description}</p>
        <div className="mozhi-tag-list">
          {tags.map((tag) => (
            <span key={tag} className="mozhi-tag">
              {tag}
            </span>
          ))}
        </div>
      </div>
    </article>
  );
}

export function TimelineList({ children }: PropsWithChildren) {
  return <div className="mozhi-timeline-list">{children}</div>;
}

export function TimelineRow({ title, period, role, description }: TimelineRowProps) {
  return (
    <div className="mozhi-timeline-row">
      <div className="mozhi-timeline-mark">{title.slice(0, 1)}</div>
      <div className="mozhi-timeline-content">
        <div className="mozhi-timeline-header">
          <span className="mozhi-timeline-title">{title}</span>
          <span className="mozhi-timeline-period">{period}</span>
        </div>
        <div className="mozhi-timeline-role">{role}</div>
        <p className="mozhi-timeline-description">{description}</p>
      </div>
    </div>
  );
}

export function SubscribePanel({
  title,
  subtitle,
  placeholder,
  buttonLabel
}: SubscribePanelProps) {
  return (
    <section className="mozhi-subscribe-box">
      <SectionHeading subtitle={subtitle} title={title} />
      <div className="mozhi-subscribe-form">
        <input className="mozhi-input" placeholder={placeholder} type="email" />
        <button className="mozhi-button" type="button">
          {buttonLabel}
        </button>
      </div>
    </section>
  );
}

export function StatusMetrics({ children }: PropsWithChildren) {
  return <div className="mozhi-metric-grid">{children}</div>;
}

export function Metric({ label, value, tone = "default" }: { label: string; value: string; tone?: "default" | "accent" | "success" | "danger" }) {
  return (
    <div className="mozhi-metric-card">
      <dt className="mozhi-metric-label">{label}</dt>
      <dd className={clsx("mozhi-metric-value", tone !== "default" && `mozhi-tone-${tone}`)}>{value}</dd>
    </div>
  );
}

export function CardField(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={clsx("mozhi-input", props.className)} />;
}
