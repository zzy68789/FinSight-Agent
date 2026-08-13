export const RESEARCH_READINESS = Object.freeze({
  WAITING_SUBJECT: 'WAITING_SUBJECT',
  RESOLVING_SUBJECT: 'RESOLVING_SUBJECT',
  SELECTING_SUBJECT: 'SELECTING_SUBJECT',
  WAITING_QUESTION: 'WAITING_QUESTION',
  WAITING_DOCUMENT: 'WAITING_DOCUMENT',
  READY: 'READY',
  SUBMITTING: 'SUBMITTING'
});

const BASE_INTENTS = Object.freeze([
  {
    value: 'COMPREHENSIVE',
    label: '综合研究',
    description: '统筹财务、估值、风险与证据完整性'
  },
  {
    value: 'VALUATION_RISK',
    label: '估值风险',
    description: '检查估值口径、风险因素与不确定性'
  },
  {
    value: 'EVENT_IMPACT',
    label: '事件影响',
    description: '围绕公告或事件重新取证并分析影响'
  }
]);

const ASSET_INTENTS = Object.freeze({
  EQUITY: {
    value: 'FINANCIAL_QUALITY',
    label: '财务质量',
    description: '聚焦盈利质量、现金流与异常变化'
  },
  ETF: {
    value: 'ETF_TRACKING',
    label: 'ETF 跟踪',
    description: '关注跟踪标的、规模、费率与跟踪风险'
  }
});

/**
 * 集中计算研究任务是否具备提交条件，避免视图组件各自维护禁用规则。
 *
 * @param {object} formState 研究启动器当前状态。
 * @returns {{status: string, message: string, canSubmit: boolean, step: number}}
 */
export function resolveResearchReadiness(formState = {}) {
  const query = String(formState.subjectQuery || '').trim();
  const question = String(formState.researchQuestion || '').trim();
  const candidateCount = Number(formState.candidateCount || 0);
  const uploadedFileCount = Number(formState.uploadedFileCount || 0);

  if (formState.isSubmitting) {
    return readiness(RESEARCH_READINESS.SUBMITTING, '正在创建研究任务，请勿重复提交', false, 3);
  }
  if (!query) {
    return readiness(RESEARCH_READINESS.WAITING_SUBJECT, '请输入证券代码或名称', false, 1);
  }
  if (query.length < 2) {
    return readiness(RESEARCH_READINESS.WAITING_SUBJECT, '请至少输入 2 个字符以搜索证券', false, 1);
  }
  if (formState.isResolving) {
    return readiness(RESEARCH_READINESS.RESOLVING_SUBJECT, '正在识别证券，请稍候', false, 1);
  }
  if (!formState.selectedSecurity) {
    if (candidateCount > 0) {
      return readiness(RESEARCH_READINESS.SELECTING_SUBJECT, '请选择并确认唯一证券', false, 1);
    }
    if (formState.searchCompleted) {
      return readiness(
        RESEARCH_READINESS.WAITING_SUBJECT,
        formState.searchError || '未识别到受支持的 A 股或 ETF',
        false,
        1
      );
    }
    return readiness(RESEARCH_READINESS.RESOLVING_SUBJECT, '正在准备证券搜索', false, 1);
  }
  if (!question) {
    return readiness(RESEARCH_READINESS.WAITING_QUESTION, '请输入研究问题', false, 2);
  }
  if (formState.searchMode === 'document' && uploadedFileCount === 0) {
    return readiness(RESEARCH_READINESS.WAITING_DOCUMENT, '仅文档检索需要至少上传一份 PDF', false, 3);
  }
  if (uploadedFileCount > 0 && !formState.isDocumentReady) {
    return readiness(RESEARCH_READINESS.WAITING_DOCUMENT, '文档正在解析，请稍候', false, 3);
  }
  return readiness(RESEARCH_READINESS.READY, '研究任务已就绪', true, 3);
}

/**
 * 根据证券资产类型返回合法的研究意图，避免股票和 ETF 意图错配。
 *
 * @param {string} assetType 资产类型。
 * @returns {Array<{value: string, label: string, description: string}>}
 */
export function researchIntentOptions(assetType) {
  const assetIntent = ASSET_INTENTS[assetType];
  const options = [...BASE_INTENTS];
  if (assetIntent) options.splice(1, 0, assetIntent);
  return options;
}

/**
 * 为已确认标的生成中性、合规的研究问题建议。
 *
 * @param {object|null} security 已确认证券。
 * @param {string} intent 研究意图。
 * @returns {string[]}
 */
export function suggestedResearchQuestions(security, intent = 'COMPREHENSIVE') {
  if (!security) return [];
  const subject = security.companyName || security.fullCode || security.ticker || '该证券';
  const common = {
    COMPREHENSIVE: [
      `${subject}近期财务表现、估值水平与主要风险之间有什么联系？`,
      `${subject}当前核心经营驱动和后续需要跟踪的证据有哪些？`,
      `基于截至研究日的公开证据，如何评价${subject}的基本面变化？`
    ],
    FINANCIAL_QUALITY: [
      `${subject}近几个报告期的盈利质量和现金流匹配度如何变化？`,
      `${subject}收入、利润与毛利率变化的主要原因和异常项是什么？`,
      `${subject}的成长性是否有现金流和资产负债表证据支持？`
    ],
    VALUATION_RISK: [
      `${subject}当前估值观察应采用哪些口径，各自有哪些局限？`,
      `${subject}的主要经营、财务和市场风险由哪些证据支持？`,
      `哪些不确定性最可能影响对${subject}估值水平的解释？`
    ],
    ETF_TRACKING: [
      `${subject}的跟踪标的、规模、费率和流动性特征如何？`,
      `${subject}可能面临哪些跟踪偏离、集中度和市场风险？`,
      `${subject}近期表现与跟踪标的差异的主要原因是什么？`
    ],
    EVENT_IMPACT: [
      `${subject}近期公告或重要事件可能影响哪些经营和财务变量？`,
      `截至研究日，市场对${subject}最新事件的解读有哪些证据与分歧？`,
      `${subject}最新事件的影响需要继续跟踪哪些公开证据？`
    ]
  };
  return common[intent] || common.COMPREHENSIVE;
}

/**
 * 只保留与主证券同资产类型、且尚未选择的可比证券候选。
 *
 * @param {object[]} candidates 后端证券搜索候选。
 * @param {object|null} primarySecurity 主证券。
 * @param {object[]} selected 已确认的可比证券。
 * @returns {object[]}
 */
export function filterComparisonCandidates(candidates, primarySecurity, selected = []) {
  if (!primarySecurity) return [];
  const blocked = new Set([
    String(primarySecurity.fullCode || '').toUpperCase(),
    ...selected.map(item => String(item.fullCode || '').toUpperCase())
  ]);
  return (candidates || []).filter(candidate => (
    candidate?.assetType === primarySecurity.assetType
    && candidate?.fullCode
    && !blocked.has(String(candidate.fullCode).toUpperCase())
  ));
}

/**
 * 向已确认列表追加一个可比证券，最多保留三个且不允许主证券或跨资产类型。
 *
 * @param {object[]} selected 已确认的可比证券。
 * @param {object} candidate 待追加候选。
 * @param {object|null} primarySecurity 主证券。
 * @returns {object[]}
 */
export function appendComparisonSecurity(selected = [], candidate, primarySecurity) {
  if (!candidate || !primarySecurity || selected.length >= 3) return [...selected];
  if (!filterComparisonCandidates([candidate], primarySecurity, selected).length) return [...selected];
  return [...selected, candidate];
}

/**
 * 为异步搜索签发单调令牌，调用方只接收最后一次输入对应的响应。
 *
 * @returns {{issue: function(): number, isCurrent: function(number): boolean, invalidate: function(): void}}
 */
export function createLatestRequestGate() {
  let currentToken = 0;
  return {
    issue() {
      currentToken += 1;
      return currentToken;
    },
    isCurrent(token) {
      return token === currentToken;
    },
    invalidate() {
      currentToken += 1;
    }
  };
}

function readiness(status, message, canSubmit, step) {
  return { status, message, canSubmit, step };
}
