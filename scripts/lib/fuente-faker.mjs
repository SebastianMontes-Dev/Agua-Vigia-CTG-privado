// Azar y nombres de faker para crearFabricaDeCuentas (ADR-087). Sin semilla, cada ejecución genera personas distintas;
// con semilla, reproduce la misma serie (útil para pruebas).

import { Faker, es_MX, es, base } from '@faker-js/faker';

export function crearFuenteFaker({ semilla } = {}) {
  // Instancia propia y no la global: dos fuentes en el mismo proceso no se pisan la semilla.
  const faker = new Faker({ locale: [es_MX, es, base] });
  if (semilla !== undefined) faker.seed(Number(semilla));

  const azar = () => faker.number.int({ min: 0, max: 0xffffffff }) / 0x100000000;
  // faker ya devuelve los dos apellidos juntos («Barreto Fierro», «Benavides de Galindo»): se separan en el primero.
  function nombrar() {
    const nombre = faker.person.firstName();
    const [apellido1, ...resto] = faker.person.lastName().split(' ');
    const apellido2 = resto.length > 0 ? resto.join(' ') : faker.person.lastName().split(' ')[0];
    return { nombre, apellido1, apellido2 };
  }
  return { azar, nombrar };
}
