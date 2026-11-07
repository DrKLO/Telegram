#ifndef LITEHTML_MASTER_CSS_H
#define LITEHTML_MASTER_CSS_H
namespace litehtml
{
    const char* const master_css = R"##(


html {
	display: block;
}

head {
	display: none
}

meta {
	display: none
}

title {
	display: none
}

link {
	display: none
}

style {
	display: none
}

script {
	display: none
}

body {
	display:block;
	margin:8px;
}

p {
	display:block;
	margin-top:1em;
	margin-bottom:1em;
}

b, strong {
	display:inline;
	font-weight:bold;
}

i, em, cite {
	display:inline;
	font-style:italic;
}

ins, u {
	text-decoration:underline
}

del, s, strike {
	text-decoration:line-through
}

center
{
	text-align:center;
	display:block;
}

a:link
{
	text-decoration: underline;
	color: #00f;
	cursor: pointer;
}

h1, h2, h3, h4, h5, h6, div {
	display:block;
}

h1 {
	font-weight:bold;
	margin-top:0.67em;
	margin-bottom:0.67em;
	font-size: 2em;
}

h2 {
	font-weight:bold;
	margin-top:0.83em;
	margin-bottom:0.83em;
	font-size: 1.5em;
}

h3 {
	font-weight:bold;
	margin-top:1em;
	margin-bottom:1em;
	font-size:1.17em;
}

h4 {
	font-weight:bold;
	margin-top:1.33em;
	margin-bottom:1.33em
}

h5 {
	font-weight:bold;
	margin-top:1.67em;
	margin-bottom:1.67em;
	font-size:.83em;
}

h6 {
	font-weight:bold;
	margin-top:2.33em;
	margin-bottom:2.33em;
	font-size:.67em;
}

br {
	display:inline-block;
}

br[clear="all"]
{
	clear:both;
}

br[clear="left"]
{
	clear:left;
}

br[clear="right"]
{
	clear:right;
}

span {
	display:inline
}

img {
	display: inline-block;
}

img[align="right"]
{
	float: right;
}

img[align="left"]
{
	float: left;
}

hr {
	display: block;
	margin-top: 0.5em;
	margin-bottom: 0.5em;
	margin-left: auto;
	margin-right: auto;
	border-style: inset;
	border-width: 1px
}


/***************** TABLES ********************/

table {
	display: table;
	border-collapse: separate;
	border-spacing: 2px;
	border-top-color:gray;
	border-left-color:gray;
	border-bottom-color:black;
	border-right-color:black;
	font-size: medium;
	font-weight: normal;
	font-style: normal;
}

tbody, tfoot, thead {
	display:table-row-group;
	vertical-align:middle;
}

tr {
	display: table-row;
	vertical-align: inherit;
	border-color: inherit;
}

td, th {
	display: table-cell;
	vertical-align: inherit;
	border-width:1px;
	padding:1px;
}

th {
	font-weight: bold;
}

table[border] {
	border-style:solid;
}

table[border^="0"] {
	border-style:none;
}

table[border] td, table[border] th {
	border-style:solid;
	border-top-color:black;
	border-left-color:black;
	border-bottom-color:gray;
	border-right-color:gray;
}

table[border^="0"] td, table[border^="0"] th {
	border-style:none;
}

table[align=left] {
   float: left;
}

table[align=right] {
   float: right;
}

table[align=center] {
   margin-left: auto;
   margin-right: auto;
}

caption {
	display: table-caption;
}

td[nowrap], th[nowrap] {
	white-space:nowrap;
}

tt, code, kbd, samp {
	font-family: monospace
}

pre, xmp, plaintext, listing {
	display: block;
	font-family: monospace;
	white-space: pre;
	margin: 1em 0
}

/***************** LISTS ********************/

ul, menu, dir {
	display: block;
	list-style-type: disc;
	margin-top: 1em;
	margin-bottom: 1em;
	margin-left: 0;
	margin-right: 0;
	padding-left: 40px
}

ol {
	display: block;
	list-style-type: decimal;
	margin-top: 1em;
	margin-bottom: 1em;
	margin-left: 0;
	margin-right: 0;
	padding-left: 40px
}

li {
	display: list-item;
}

ul ul, ol ul {
	list-style-type: circle;
}

ol ol ul, ol ul ul, ul ol ul, ul ul ul {
	list-style-type: square;
}

dd {
	display: block;
	margin-left: 40px;
}

dl {
	display: block;
	margin-top: 1em;
	margin-bottom: 1em;
	margin-left: 0;
	margin-right: 0;
}

dt {
	display: block;
}

ol ul, ul ol, ul ul, ol ol {
	margin-top: 0;
	margin-bottom: 0
}

blockquote {
	display: block;
	margin-top: 1em;
	margin-bottom: 1em;
	margin-left: 40px;
	margin-right: 40px;
}

/*********** FORM ELEMENTS ************/

form {
	display: block;
	margin-top: 0em;
}

option {
	display: none;
}

input, textarea, keygen, select, button, isindex {
	margin: 0em;
	color: initial;
	line-height: normal;
	text-transform: none;
	text-indent: 0;
	text-shadow: none;
	display: inline-block;
}
input[type="hidden"] {
	display: none;
}


article, aside, footer, header, hgroup, nav, section
{
	display: block;
}

sub {
	vertical-align: sub;
	font-size: smaller;
}

sup {
	vertical-align: super;
	font-size: smaller;
}

figure {
	display: block;
	margin-top: 1em;
	margin-bottom: 1em;
	margin-left: 40px;
	margin-right: 40px;
}

figcaption {
	display: block;
}

)##";
}
#endif // LITEHTML_MASTER_CSS_H
