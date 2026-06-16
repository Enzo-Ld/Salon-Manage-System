const fs = require("fs");
const path = require("path");

const entities = [
  {
    page: "表3-3 用户表",
    name: "用户",
    attrs: ["id", "用户名", "登录密码", "用户昵称", "手机号", "头像地址", "启用状态", "创建时间", "更新时间"],
  },
  {
    page: "表3-4 技师表",
    name: "技师",
    attrs: ["id", "关联用户ID", "技师姓名", "技师等级", "综合评分", "平均评价分", "评价数量", "人工调整评分", "技师状态", "创建时间", "更新时间"],
  },
  {
    page: "表3-5 服务项目表",
    name: "服务项目",
    attrs: ["id", "服务分类ID", "服务名称", "服务简介", "服务图片地址", "服务时长", "服务价格", "服务状态"],
  },
  {
    page: "表3-6 排班表",
    name: "排班",
    attrs: ["id", "技师ID", "工作日期", "开始时间", "结束时间", "排班状态"],
  },
  {
    page: "表3-7 预约表",
    name: "预约",
    attrs: ["id", "预约用户ID", "技师ID", "服务项目ID", "预约开始时间", "预约结束时间", "服务时长快照", "预约状态", "支付状态", "支付方式", "支付时间", "支付单号", "预约金额", "技师分配方式", "预约备注", "取消时间", "创建时间", "更新时间"],
  },
  {
    page: "表3-8 请假记录表",
    name: "请假记录",
    attrs: ["id", "技师ID", "请假开始时间", "请假结束时间", "请假原因", "审核状态", "创建人用户ID", "审核人用户ID", "审核时间", "审核备注", "创建时间", "更新时间"],
  },
  {
    page: "表3-9 商品订单表",
    name: "商品订单",
    attrs: ["id", "下单用户ID", "订单总金额", "实付金额", "商品总数量", "订单状态", "支付状态", "支付方式", "支付单号", "支付时间", "关闭时间", "收货人姓名", "收货人电话", "省份", "城市", "区县", "详细地址", "地址标签", "物流公司", "物流单号", "发货时间", "售后状态", "售后原因", "售后申请时间", "售后处理时间", "售后备注", "创建时间", "更新时间"],
  },
  {
    page: "表3-10 退款记录表",
    name: "退款记录",
    attrs: ["id", "支付订单ID", "退款单号", "退款金额", "退款状态", "退款原因", "退款渠道", "退款模式", "第三方退款单号", "退款回调内容", "退款完成时间", "创建时间", "更新时间"],
  },
  {
    page: "表3-11 系统通知表",
    name: "系统通知",
    attrs: ["id", "接收用户ID", "通知分类", "通知标题", "通知内容", "跳转路径", "阅读状态", "阅读时间", "创建时间"],
  },
  {
    page: "表3-12 聊天会话表",
    name: "聊天会话",
    attrs: ["id", "会话用户A", "会话用户B", "更新时间"],
  },
  {
    page: "表3-13 聊天消息表",
    name: "聊天消息",
    attrs: ["id", "聊天会话ID", "发送人ID", "接收人ID", "消息内容", "创建时间"],
  },
  {
    page: "表3-14 AI咨询记录表",
    name: "AI咨询记录",
    attrs: ["id", "用户ID", "咨询标题", "咨询阶段", "上传图片地址", "用户输入内容", "AI总结内容", "形象分析摘要", "发型推荐JSON", "服务推荐JSON", "商品推荐JSON", "护理建议JSON", "用户意图标签JSON", "用户偏好JSON", "推荐结果快照JSON", "最后消息时间", "免责声明", "分析模式", "AI原始响应", "创建时间", "更新时间"],
  },
  {
    page: "表3-15 AI咨询消息表",
    name: "AI咨询消息",
    attrs: ["id", "咨询记录ID", "用户ID", "消息角色", "消息内容", "消息图片地址", "消息扩展数据JSON", "创建时间"],
  },
];

function esc(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function makeCell(id, value, style, x, y, w, h, extra = "") {
  return `    <mxCell id="${id}" value="${esc(value)}" style="${style}" vertex="1" parent="1"${extra}>
      <mxGeometry x="${x}" y="${y}" width="${w}" height="${h}" as="geometry"/>
    </mxCell>`;
}

function makeEdge(id, source, target) {
  return `    <mxCell id="${id}" style="endArrow=none;html=1;rounded=0;strokeColor=#666666;strokeWidth=1;" edge="1" parent="1" source="${source}" target="${target}">
      <mxGeometry relative="1" as="geometry"/>
    </mxCell>`;
}

function positions(count) {
  const centerX = 430;
  const centerY = 300;
  const leftX = 65;
  const rightX = 670;
  const topY = 60;
  const bottomY = 560;
  const topSlots = Math.min(2, Math.max(1, Math.floor(count / 8)));
  const bottomSlots = Math.min(2, Math.max(1, count > 10 ? 2 : 1));
  const sideCount = count - topSlots - bottomSlots;
  const leftCount = Math.ceil(sideCount / 2);
  const rightCount = sideCount - leftCount;
  const result = [];

  for (let i = 0; i < topSlots; i += 1) {
    result.push({ x: centerX - 165 + i * 240, y: topY });
  }
  for (let i = 0; i < leftCount; i += 1) {
    const gap = leftCount === 1 ? 0 : 390 / (leftCount - 1);
    result.push({ x: leftX, y: 110 + i * gap });
  }
  for (let i = 0; i < rightCount; i += 1) {
    const gap = rightCount === 1 ? 0 : 390 / (rightCount - 1);
    result.push({ x: rightX, y: 110 + i * gap });
  }
  for (let i = 0; i < bottomSlots; i += 1) {
    result.push({ x: centerX - 165 + i * 240, y: bottomY });
  }
  return result.slice(0, count);
}

function diagram(entity, index) {
  const entityId = `e${index}`;
  const attrPositions = positions(entity.attrs.length);
  const cells = [
    '    <mxCell id="0"/>',
    '    <mxCell id="1" parent="0"/>',
    makeCell(
      entityId,
      entity.name,
      "rounded=1;whiteSpace=wrap;html=1;arcSize=8;strokeColor=#666666;fillColor=#ffffff;fontSize=14;",
      390,
      285,
      95,
      38,
    ),
  ];

  entity.attrs.forEach((attr, i) => {
    const id = `${entityId}_a${i}`;
    const p = attrPositions[i];
    cells.push(makeCell(id, attr, "ellipse;whiteSpace=wrap;html=1;aspect=fixed;strokeColor=#666666;fillColor=#ffffff;fontSize=12;", p.x, p.y, 128, 42));
    cells.push(makeEdge(`${entityId}_edge${i}`, entityId, id));
  });

  return `  <diagram id="diagram-${index}" name="${esc(entity.page)}">
  <mxGraphModel dx="1100" dy="800" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="900" pageHeight="650" math="0" shadow="0">
    <root>
${cells.join("\n")}
    </root>
  </mxGraphModel>
  </diagram>`;
}

const xml = `<mxfile host="app.diagrams.net" modified="2026-05-24T00:00:00.000Z" agent="Codex" version="24.7.17" type="device">
${entities.map(diagram).join("\n")}
</mxfile>
`;

const outputPath = path.join(__dirname, "..", "docs", "核心实体ER图.drawio");
fs.writeFileSync(outputPath, xml, "utf8");
console.log(outputPath);
