import { test } from 'node:test';
import assert from 'node:assert/strict';
import { esBaseLocal } from '../lib/base-local.mjs';

test('reconoce las bases locales', () => {
  assert.ok(esBaseLocal('mongodb://localhost:27017/?directConnection=true'));
  assert.ok(esBaseLocal('mongodb://127.0.0.1:27017/aguavigia'));
  assert.ok(esBaseLocal('mongodb://[::1]:27017/'));
  assert.ok(esBaseLocal('mongodb://mongo:27017/?directConnection=true'));
});

test('el servicio mongo de compose solo cuenta cuando se permite', () => {
  assert.equal(esBaseLocal('mongodb://mongo:27017/', { permitirServicioMongo: false }), false);
});

test('rechaza una URI que solo empieza como una local pero apunta a otro host', () => {
  assert.equal(esBaseLocal('mongodb://mongo:x@servidor-ajeno.example/'), false);
  assert.equal(esBaseLocal('mongodb://localhost:x@servidor-ajeno.example:27017/'), false);
  assert.equal(esBaseLocal('mongodb://localhost.ajeno.example/'), false);
  assert.equal(esBaseLocal('esto no es una uri'), false);
});
