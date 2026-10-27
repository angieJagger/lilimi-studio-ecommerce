import { Component, inject, input } from '@angular/core';
import { PRIMARY_OUTLET, Router, RouterLink, UrlSegment, UrlTree } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';

@Component({
  selector: 'app-language-switcher',
  imports: [RouterLink],
  template: `
    <nav [class.expanded]="expanded()" [attr.aria-label]="transloco.getActiveLang() === 'pl' ? 'Język strony' : 'Website language'">
      @if (expanded()) {
        <span class="current-language">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true" focusable="false">
            <circle cx="12" cy="12" r="9" /><ellipse cx="12" cy="12" rx="4" ry="9" /><path d="M3 12h18" />
          </svg>
          {{ transloco.getActiveLang() === 'pl' ? 'Język: Polski' : 'Language: English' }}
        </span>
        <a class="alternative-language" [routerLink]="languageUrl(transloco.getActiveLang() === 'pl' ? 'en' : 'pl')"
          [attr.lang]="transloco.getActiveLang() === 'pl' ? 'en' : 'pl'"
          [attr.hreflang]="transloco.getActiveLang() === 'pl' ? 'en' : 'pl'">
          {{ transloco.getActiveLang() === 'pl' ? 'English' : 'Polski' }}
        </a>
      } @else {
        <a class="compact" [routerLink]="languageUrl(transloco.getActiveLang() === 'pl' ? 'en' : 'pl')"
          [attr.aria-label]="transloco.getActiveLang() === 'pl' ? 'Zmień język na angielski' : 'Switch language to Polish'"
          [attr.title]="transloco.getActiveLang() === 'pl' ? 'Zmień język na angielski' : 'Switch language to Polish'">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true" focusable="false">
            <circle cx="12" cy="12" r="9" /><ellipse cx="12" cy="12" rx="4" ry="9" /><path d="M3 12h18" />
          </svg>
          {{ transloco.getActiveLang() === 'pl' ? 'PL' : 'EN' }}
        </a>
      }
    </nav>
  `,
  styles: `
    :host { display: block; }
    nav, .current-language, a { display: flex; align-items: center; gap: 8px; }
    nav { color: var(--text-secondary); font-size: 0.875rem; }
    svg { width: 18px; height: 18px; flex-shrink: 0; }
    a {
      min-height: 44px;
      color: var(--text-primary);
      border-radius: var(--radius-4);
    }
    .compact {
      padding-inline: 8px;
      color: var(--text-inverse);
      font-weight: 600;
      text-decoration: none;
    }
    .compact:hover { color: var(--header-link-hover); }
    .expanded {
      flex-wrap: wrap;
      justify-content: space-between;
      gap: 8px 24px;
      border-top: 1px solid var(--border-brand-soft);
      padding-top: 16px;
    }
    .alternative-language { color: var(--logo-dark); text-underline-offset: 4px; font-weight: 600; }
    a:hover { color: var(--logo-dark); }
    a:focus-visible { outline: 2px solid var(--border-brand); outline-offset: 2px; }
  `,
})
export class LanguageSwitcher {
  readonly expanded = input(false);
  private readonly router = inject(Router);
  protected readonly transloco = inject(TranslocoService);

  protected languageUrl(language: 'pl' | 'en'): UrlTree {
    const url = this.router.parseUrl(this.router.url);
    const primary = url.root.children[PRIMARY_OUTLET];
    if (primary?.segments.length) {
      primary.segments[0] = new UrlSegment(language, primary.segments[0].parameters);
      return url;
    }
    return this.router.createUrlTree(['/', language], {
      queryParams: url.queryParams,
      fragment: url.fragment ?? undefined,
    });
  }
}