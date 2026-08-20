import MarkdownIt from 'markdown-it';
import mk from 'markdown-it-katex';

/** 仅允许报告跳转到站内证据锚点或 HTTP(S) 原始来源。 */
export function safeReportLink(value) {
  const link = String(value || '').trim();
  if (link.startsWith('#')) return link;
  return /^https?:\/\//i.test(link) ? link : '';
}

/** 创建所有报告视图共用的安全 Markdown 渲染器。 */
export function createReportMarkdown() {
  const markdown = new MarkdownIt({
    html: false,
    linkify: true,
    typographer: true
  });
  markdown.use(mk);
  markdown.validateLink = link => Boolean(safeReportLink(link));

  const defaultLinkOpen = markdown.renderer.rules.link_open
    || ((tokens, index, options, env, self) => self.renderToken(tokens, index, options));
  markdown.renderer.rules.link_open = (tokens, index, options, env, self) => {
    const href = safeReportLink(tokens[index].attrGet('href'));
    if (!href) tokens[index].attrSet('href', '#');
    if (href && !href.startsWith('#')) {
      tokens[index].attrSet('target', '_blank');
      tokens[index].attrSet('rel', 'noopener noreferrer');
    }
    return defaultLinkOpen(tokens, index, options, env, self);
  };
  return markdown;
}
