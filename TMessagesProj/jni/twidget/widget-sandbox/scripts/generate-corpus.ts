// Phase 6: generate realistic widgets with the Claude API.
//
// Two batches from the same prompt list: one where the `show_widget` tool
// description carries the runtime profile (docs/widget-runtime-profile.md),
// one where it does not. Both land in corpus/generated/{with,without}-profile/
// and are checked by `pnpm test:generated`.
//
// Usage:  ANTHROPIC_API_KEY=… pnpm generate-corpus [--model claude-sonnet-5-5] [--count 20] [--only with|without]

import Anthropic from '@anthropic-ai/sdk';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
const opt = (name: string, dflt: string) => { const i = args.indexOf('--' + name); return i >= 0 ? args[i + 1] : dflt; };
const MODEL = opt('model', 'claude-sonnet-5-5');
const COUNT = Number(opt('count', '20'));
const ONLY = opt('only', '');

/** Realistic requests a Telegram user might send to a bot that can show widgets. */
const PROMPTS = [
  'Show me a mortgage calculator: loan amount, interest rate, years → monthly payment and total interest.',
  'Make an interactive quiz about the solar system with 5 questions and a score at the end.',
  'Visualize this weekly budget as a chart: rent 1200, groceries 450, transport 180, entertainment 220, savings 400.',
  'Build a tip calculator with a slider for the tip percentage and a way to split the bill between people.',
  'Create a Pomodoro timer with start/pause/reset and a visual progress ring.',
  'Show a comparison table of three laptops (price, weight, battery, screen) that I can sort by each column.',
  'Make a BMI calculator with metric and imperial units and a colored result scale.',
  'Draw a line chart of monthly temperatures for Berlin vs Madrid (make up plausible data).',
  'Create a unit converter for length, weight and temperature.',
  'Show me an interactive multiplication table where I can highlight a row and column by hovering.',
  'Make a to-do list widget where I can add, check off and delete tasks.',
  'Create a countdown to New Year with days, hours, minutes and seconds.',
  'Visualize a simple project timeline (Gantt-style) for a 6-week website build.',
  'Build a color palette generator: pick a base color and show 5 harmonious shades with hex codes I can tap to copy.',
  'Create a flashcard widget for 8 Spanish words with flip animation and “know / don’t know” buttons.',
  'Make a bar chart of the top 6 programming languages by popularity with tooltips.',
  'Show a tic-tac-toe game against a simple computer opponent.',
  'Create a currency converter for USD, EUR, GBP and JPY with editable rates.',
  'Make a step-by-step recipe card for pancakes with checkboxes for ingredients and a timer for each step.',
  'Build a habit tracker grid for one month where I can tap days to mark them done, with a streak counter.',
  'Draw an SVG pie chart of a day: sleep 8h, work 8h, commute 1h, meals 2h, leisure 5h — with a legend.',
  'Create a password strength checker with live feedback as I type.',
  'Make a scatter plot of 30 random points with a trend line and axes.',
  'Build a simple drawing pad where I can doodle with a finger and clear the canvas.',
];

const profileDoc = fs.readFileSync(path.join(root, 'docs', 'widget-runtime-profile.md'), 'utf8');

const BASE_DESCRIPTION =
  'Render an interactive HTML widget inside the chat message. Pass one complete, self-contained HTML document (inline <style> and <script>).';

const WITH_PROFILE = `${BASE_DESCRIPTION}

The widget runs in a restricted runtime. Follow this profile exactly:

${profileDoc}`;

function tool(description: string): Anthropic.Tool {
  return {
    name: 'show_widget',
    description,
    input_schema: {
      type: 'object',
      properties: { html: { type: 'string', description: 'The complete HTML of the widget.' } },
      required: ['html'],
      additionalProperties: false,
    },
    strict: true,
  };
}

const SYSTEM =
  'You are the assistant behind a Telegram bot. When the user asks for anything visual or interactive, call the show_widget tool exactly once with the complete widget, then reply with one short sentence. Do not describe the code in your reply.';

const slug = (s: string) => s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 40);

interface Entry { index: number; prompt: string; file: string | null; stop: string; usage: unknown; error?: string }

async function generate(client: Anthropic, batch: 'with' | 'without') {
  const dir = path.join(root, 'corpus', 'generated', `${batch}-profile`);
  fs.mkdirSync(dir, { recursive: true });
  const manifest: Entry[] = [];
  const prompts = PROMPTS.slice(0, COUNT);
  for (let i = 0; i < prompts.length; i++) {
    const prompt = prompts[i];
    const name = `${String(i + 1).padStart(2, '0')}-${slug(prompt)}.html`;
    const file = path.join(dir, name);
    if (fs.existsSync(file)) { console.log(`[${batch}] ${name} exists, skipping`); manifest.push({ index: i, prompt, file: name, stop: 'cached', usage: null }); continue; }
    process.stdout.write(`[${batch}] ${i + 1}/${prompts.length} ${prompt.slice(0, 60)}… `);
    try {
      const stream = client.messages.stream({
        model: MODEL,
        max_tokens: 32000,
        system: SYSTEM,
        tools: [{ ...tool(batch === 'with' ? WITH_PROFILE : BASE_DESCRIPTION), eager_input_streaming: true } as Anthropic.Tool],
        tool_choice: { type: 'auto' },
        messages: [{ role: 'user', content: prompt }],
      });
      const message = await stream.finalMessage();
      const use = message.content.find((b): b is Anthropic.ToolUseBlock => b.type === 'tool_use' && b.name === 'show_widget');
      const html = use && typeof (use.input as any)?.html === 'string' ? ((use.input as any).html as string) : null;
      if (message.stop_reason === 'refusal' || message.stop_reason === 'max_tokens' || !html) {
        console.log(`no widget (stop_reason=${message.stop_reason})`);
        manifest.push({ index: i, prompt, file: null, stop: message.stop_reason ?? '', usage: message.usage });
        continue;
      }
      fs.writeFileSync(file, html);
      console.log(`${html.length} bytes`);
      manifest.push({ index: i, prompt, file: name, stop: message.stop_reason ?? '', usage: message.usage });
    } catch (e) {
      if (e instanceof Anthropic.RateLimitError) { console.log('rate limited, waiting 30 s'); await new Promise((r) => setTimeout(r, 30_000)); i--; continue; }
      if (e instanceof Anthropic.APIError) console.log(`API error ${e.status}: ${e.message}`);
      else console.log('error:', (e as Error).message);
      manifest.push({ index: i, prompt, file: null, stop: 'error', usage: null, error: String((e as Error).message) });
    }
  }
  fs.writeFileSync(path.join(dir, 'manifest.json'), JSON.stringify({ model: MODEL, generated: new Date().toISOString(), entries: manifest }, null, 2));
}

const client = new Anthropic();
for (const batch of ['with', 'without'] as const) {
  if (ONLY && ONLY !== batch) continue;
  await generate(client, batch);
}
console.log('done. Run: pnpm test:generated');
